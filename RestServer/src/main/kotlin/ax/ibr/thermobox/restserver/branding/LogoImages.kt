package ax.ibr.thermobox.restserver.branding

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the logo the server shows comes from. */
enum class LogoSource { CUSTOM, SITE, DEFAULT }

/** An image ready to be served: its bytes, its media type, and where it comes from. */
class Logo(val bytes: ByteArray, val type: String, val source: LogoSource) {
    /** Changes with the image: used as ETag and as version in the logo URL. */
    val version: String = MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }

    val isSvg: Boolean get() = type == LogoImages.SVG
}

/** Recognizes, checks and resizes the logo images (uploaded or found on a website). */
object LogoImages {

    const val PNG = "image/png"
    const val JPEG = "image/jpeg"
    const val GIF = "image/gif"
    const val WEBP = "image/webp"
    const val ICO = "image/x-icon"
    const val SVG = "image/svg+xml"

    /** Largest side of a logo kept by the server. */
    const val MAX_SIDE = 1024
    /** Bigger images are refused before being decoded. */
    private const val MAX_PIXELS = 25_000_000L

    /** Media type from the first bytes of the file, null if it isn't an image we serve. */
    fun sniff(bytes: ByteArray): String? {
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            starts(0xFF, 0xD8, 0xFF) -> JPEG
            starts(0x47, 0x49, 0x46, 0x38) -> GIF
            starts(0x52, 0x49, 0x46, 0x46) && bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
            starts(0x00, 0x00, 0x01, 0x00) -> ICO
            looksLikeSvg(bytes) -> SVG
            else -> null
        }
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        return (head.startsWith("<svg") || head.startsWith("<?xml") || head.startsWith("<!--")) && "<svg" in head
    }

    /** PNG, JPEG and GIF can be decoded and resized here; the other formats are served as they are. */
    fun isRaster(type: String): Boolean = type == PNG || type == JPEG || type == GIF

    /**
     * A raster image as a PNG whose largest side is at most [maxSide] (never enlarged), alpha kept.
     * @throws IllegalArgumentException if the image cannot be read or is too large
     */
    fun toPng(bytes: ByteArray, maxSide: Int = MAX_SIDE): ByteArray {
        val source = decode(bytes)
        val scale = minOf(1.0, maxSide.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(target, "png", it) }.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: throw IllegalArgumentException("Unrecognized image (PNG, JPEG, GIF or SVG).")
            try {
                reader.input = input
                if (reader.getWidth(0).toLong() * reader.getHeight(0) > MAX_PIXELS) {
                    throw IllegalArgumentException("The image is too large.")
                }
                return reader.read(0)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Refuses an SVG that could run code or load something when opened on its own: scripts, event
     * handlers, javascript: links, embedded HTML, external references and DTDs.
     * @throws IllegalArgumentException with the reason
     */
    fun checkSvg(bytes: ByteArray) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = try {
            // No error handler: the parser would print its errors on stderr
            factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid SVG file.")
        }
        val root = document.documentElement
        require(root.localName == "svg") { "Invalid SVG file." }
        val all = document.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i)
            val name = (e.localName ?: e.nodeName).lowercase()
            require(name != "script" && name != "foreignobject") { "An SVG logo cannot contain <$name>." }
            val attributes = e.attributes
            for (j in 0 until attributes.length) {
                val a = attributes.item(j)
                val attr = (a.localName ?: a.nodeName).lowercase()
                val value = a.nodeValue.trim().lowercase()
                require(!attr.startsWith("on")) { "An SVG logo cannot contain event handlers ($attr)." }
                if (attr == "href" || attr == "src") {
                    require(value.startsWith("#") || value.startsWith("data:image/")) { "An SVG logo cannot reference other files." }
                }
                require("javascript:" !in value) { "An SVG logo cannot contain javascript: links." }
            }
        }
    }
}
