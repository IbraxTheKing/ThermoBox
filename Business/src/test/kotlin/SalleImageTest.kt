package ax.ibr.thermobox.business.images

import ax.ibr.thermobox.business.implementations.SalleImageServiceImpl
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.common.services.SalleImageService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SalleImageTest {

    private class FaussesImages : SalleImageService {
        val lignes = mutableListOf<SalleImage>()
        private var nextId = 1L
        override fun getBySalle(salle: Salle) = lignes.firstOrNull { it.salle.id == salle.id }
        override fun getDates() = lignes.associate { it.salle.id!! to it.updated }
        override fun add(t: SalleImage) { t.id = nextId++; lignes += t }
        override fun update(t: SalleImage) {}
        override fun remove(t: SalleImage) { lignes -= t }
        override fun getAll() = lignes.toList()
        override fun getById(id: Long) = lignes.firstOrNull { it.id == id }
    }

    private fun png(width: Int, height: Int, type: Int = BufferedImage.TYPE_INT_ARGB): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, type), "png", out)
        return out.toByteArray()
    }

    private fun lire(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun `une image est reencodee en JPEG et reduite`() {
        val jpeg = ImageProcessor.toJpeg(png(4000, 1000))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), jpeg.copyOfRange(0, 2))
        val image = lire(jpeg)
        assertEquals(1600, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun `une petite image garde sa taille`() {
        val image = lire(ImageProcessor.toJpeg(png(300, 200, BufferedImage.TYPE_INT_RGB)))
        assertEquals(300, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun `un fichier qui n'est pas une image est refuse`() {
        assertThrows(IllegalArgumentException::class.java) { ImageProcessor.toJpeg("<svg onload=alert(1)>".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { ImageProcessor.toJpeg(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) {
            ImageProcessor.toJpeg(ByteArray(ImageProcessor.MAX_UPLOAD_BYTES + 1))
        }
    }

    @Test
    fun `l'orientation EXIF est appliquee`() {
        // 2×3 image whose pixels all differ, so any wrong mapping shows.
        val source = BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 2) for (y in 0 until 3) source.setRGB(x, y, (x * 3 + y + 1) * 0x101010)
        val w = 2
        val h = 3
        // Where pixel (x, y) of the stored photo must end up, for each orientation.
        val attendu: Map<Int, (Int, Int) -> Pair<Int, Int>> = mapOf(
            1 to { x, y -> x to y },
            2 to { x, y -> (w - 1 - x) to y },
            3 to { x, y -> (w - 1 - x) to (h - 1 - y) },
            4 to { x, y -> x to (h - 1 - y) },
            5 to { x, y -> y to x },
            6 to { x, y -> (h - 1 - y) to x },
            7 to { x, y -> (h - 1 - y) to (w - 1 - x) },
            8 to { x, y -> y to (w - 1 - x) },
        )
        for ((orientation, position) in attendu) {
            val tournee = ImageProcessor.orient(source, orientation)
            for (x in 0 until w) for (y in 0 until h) {
                val (nx, ny) = position(x, y)
                assertEquals(source.getRGB(x, y), tournee.getRGB(nx, ny), "orientation $orientation, pixel ($x, $y)")
            }
        }
    }

    @Test
    fun `l'orientation est lue dans l'EXIF d'un JPEG`() {
        fun jpeg(motorola: Boolean, orientation: Int): ByteArray {
            fun u16(v: Int) = if (motorola) byteArrayOf((v shr 8).toByte(), v.toByte()) else byteArrayOf(v.toByte(), (v shr 8).toByte())
            fun u32(v: Int) = if (motorola) u16(v shr 16) + u16(v and 0xFFFF) else u16(v and 0xFFFF) + u16(v shr 16)
            val tiff = (if (motorola) "MM".toByteArray() else "II".toByteArray()) + u16(42) + u32(8) +
                u16(1) + u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0) + u32(0)
            val app1 = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
            val len = app1.size + 2
            return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) +
                app1 + byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2)
        }
        assertEquals(6, ImageProcessor.exifOrientation(jpeg(true, 6)))
        assertEquals(8, ImageProcessor.exifOrientation(jpeg(false, 8)))
        assertEquals(1, ImageProcessor.exifOrientation(png(2, 2)))
        assertEquals(1, ImageProcessor.exifOrientation(jpeg(true, 6).copyOf(14))) // truncated
    }

    @Test
    fun `une nouvelle image remplace la precedente`() {
        val donnees = FaussesImages()
        val service = SalleImageServiceImpl(donnees)
        val salle = Salle("Accueil").apply { id = 10 }

        service.add(SalleImage(salle, "image/png", png(50, 50)))
        val premiere = donnees.lignes.single().data
        service.add(SalleImage(salle, "image/png", png(80, 40)))

        val image = donnees.lignes.single()
        assertEquals("image/jpeg", image.contentType)
        assertEquals(80, lire(image.data).width)
        assertEquals(false, premiere.contentEquals(image.data))
    }
}
