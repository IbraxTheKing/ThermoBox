package ax.ibr.thermobox.business.images

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

/**
 * Turns an uploaded file into the image that is stored: decoded (so anything that isn't really
 * an image is refused), turned upright, reduced, and re-encoded as JPEG. Re-encoding also drops
 * the file's metadata, such as the GPS position of a phone photo.
 */
object ImageProcessor {

    const val CONTENT_TYPE = "image/jpeg"
    const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

    /** Longest side of a stored image: enough for a full-width banner. */
    private const val MAX_SIDE = 1600

    // Checked before decoding: a small file can declare a huge image and exhaust the memory.
    private const val MAX_PIXELS = 40_000_000L

    private const val QUALITY = 0.85f
    private const val UNSUPPORTED = "Unrecognized image format (JPEG, PNG, GIF or BMP)."

    /** @throws IllegalArgumentException if [bytes] is not a readable image, or is too big. */
    fun toJpeg(bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty()) { "The file is empty." }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "The image must not exceed ${MAX_UPLOAD_BYTES / 1024 / 1024} Mo." }
        val image = orient(decode(bytes), exifOrientation(bytes))
        return encode(resize(image))
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException(UNSUPPORTED)
        input.use {
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: throw IllegalArgumentException(UNSUPPORTED)
            try {
                reader.input = input
                val pixels = reader.getWidth(0).toLong() * reader.getHeight(0)
                require(pixels <= MAX_PIXELS) { "The image is too large (${MAX_PIXELS / 1_000_000} megapixels at most)." }
                return reader.read(0)
            } catch (e: IOException) {
                throw IllegalArgumentException(UNSUPPORTED, e) // truncated file, CMYK JPEG…
            } finally {
                reader.dispose()
            }
        }
    }

    private fun resize(source: BufferedImage): BufferedImage {
        val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(source.width, source.height))
        val width = maxOf(1, (source.width * scale).roundToInt())
        val height = maxOf(1, (source.height * scale).roundToInt())
        // Halving step by step: a single bilinear pass over a big reduction skips pixels and looks grainy.
        var image = source
        while (image.width / 2 >= width && image.height / 2 >= height) {
            image = draw(image, image.width / 2, image.height / 2)
        }
        // Always drawn once more, which also gives the RGB image JPEG needs.
        return draw(image, width, height)
    }

    private fun draw(source: BufferedImage, width: Int, height: Int, transform: AffineTransform? = null): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color.WHITE // JPEG has no transparency
            g.fillRect(0, 0, width, height)
            if (transform != null) g.drawImage(source, transform, null)
            else g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun encode(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    /**
     * Phones store photos as the sensor saw them and put the rotation in the EXIF orientation
     * tag, which ImageIO ignores; once the metadata is dropped the photo would stay sideways.
     */
    internal fun orient(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val t = AffineTransform()
        when (orientation) {
            2 -> { t.translate(w, 0.0); t.scale(-1.0, 1.0) }                  // mirrored
            3 -> { t.translate(w, h); t.rotate(Math.PI) }                      // upside down
            4 -> { t.translate(0.0, h); t.scale(1.0, -1.0) }                  // mirrored vertically
            5 -> { t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }                // transposed
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2) }               // turned left: rotate right
            7 -> { t.translate(h, w); t.scale(1.0, -1.0); t.rotate(Math.PI / 2) } // transversed
            8 -> { t.translate(0.0, w); t.rotate(-Math.PI / 2) }              // turned right: rotate left
        }
        return if (orientation >= 5) draw(image, image.height, image.width, t)
        else draw(image, image.width, image.height, t)
    }

    /** EXIF orientation of a JPEG (1 = upright), read from its APP1 segment. */
    internal fun exifOrientation(bytes: ByteArray): Int {
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        fun u16be(i: Int) = (u8(i) shl 8) or u8(i + 1)
        try {
            if (u16be(0) != 0xFFD8) return 1
            var pos = 2
            while (pos + 4 <= bytes.size && u8(pos) == 0xFF) {
                val marker = u8(pos + 1)
                if (marker == 0xDA) return 1 // image data starts: no EXIF before it
                val length = u16be(pos + 2)
                val exif = pos + 4
                if (marker == 0xE1 && String(bytes, exif, 4, Charsets.US_ASCII) == "Exif") {
                    val tiff = exif + 6
                    val little = u8(tiff) == 'I'.code
                    fun u16(i: Int) = if (little) u8(i) or (u8(i + 1) shl 8) else u16be(i)
                    fun u32(i: Int) = if (little) u16(i) or (u16(i + 2) shl 16) else (u16(i) shl 16) or u16(i + 2)
                    val ifd = tiff + u32(tiff + 4)
                    repeat(u16(ifd)) { n ->
                        val entry = ifd + 2 + n * 12
                        if (u16(entry) == 0x0112) return u16(entry + 8)
                    }
                    return 1
                }
                pos += 2 + length
            }
        } catch (_: IndexOutOfBoundsException) {
            // Malformed metadata: the image itself may still be fine.
        }
        return 1
    }
}
