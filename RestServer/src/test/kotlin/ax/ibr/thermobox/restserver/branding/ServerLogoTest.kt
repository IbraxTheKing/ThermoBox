package ax.ibr.thermobox.restserver.branding

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO

class ServerLogoTest {

    private fun png(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()

    private fun size(png: ByteArray) = ImageIO.read(ByteArrayInputStream(png)).let { it.width to it.height }

    @Test
    fun `the ThermoBox logo is packaged in every size`() {
        assertTrue(String(ServerLogo.DEFAULT_SVG.bytes).startsWith("<svg"))
        for ((s, logo) in ServerLogo.DEFAULT_PNG) assertEquals(s to s, size(logo.bytes))
    }

    @Test
    fun `the site of a server is its parent domain`() {
        assertEquals("google.com", SiteLogo.siteOf("thermobox.google.com"))
        assertEquals("example.co.uk", SiteLogo.siteOf("Thermobox.Example.co.uk."))
        assertEquals("example.com", SiteLogo.siteOf("example.com"))
        for (h in listOf(null, "", "localhost", "thermobox", "192.168.1.20", "[::1]", "nas.local", "srv.lan", "a.b.internal", "x y.com")) {
            assertNull(SiteLogo.siteOf(h), h)
        }
    }

    @Test
    fun `the largest declared icon comes first`() {
        val html = """<head><link rel="icon" href="/favicon-32.png" sizes="32x32">
            <link href='/big.png' rel='icon' sizes='192x192'><link rel="apple-touch-icon" href="/apple.png">
            <link rel="stylesheet" href="/s.css"><LINK REL="shortcut icon" HREF="/fav.ico"></head>"""
        assertEquals(listOf("/big.png", "/apple.png", "/favicon-32.png", "/fav.ico"), SiteLogo.icons(html))
    }

    @Test
    fun `only public addresses are fetched`() {
        for (a in listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1")) {
            assertFalse(SiteLogo.isPublic(InetAddress.getByName(a)), a)
        }
        assertTrue(SiteLogo.isPublic(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun `images are recognized, resized and checked`() {
        assertEquals(LogoImages.PNG, LogoImages.sniff(png(4, 4)))
        assertEquals(LogoImages.ICO, LogoImages.sniff(byteArrayOf(0, 0, 1, 0, 1, 0)))
        assertEquals(LogoImages.SVG, LogoImages.sniff("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertNull(LogoImages.sniff("<html>".toByteArray()))
        assertEquals(1024 to 512, size(LogoImages.toPng(png(2048, 1024))))
        assertEquals(10 to 10, size(LogoImages.toPng(png(10, 10))))
        LogoImages.checkSvg("""<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1" fill="red"/></svg>""".toByteArray())
        for (bad in listOf(
            """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""",
            """<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>""",
            """<svg xmlns="http://www.w3.org/2000/svg" xmlns:x="http://www.w3.org/1999/xlink"><image x:href="http://evil/x.png"/></svg>""",
            """<!DOCTYPE svg [<!ENTITY e SYSTEM "file:///etc/passwd">]><svg xmlns="http://www.w3.org/2000/svg">&e;</svg>""",
        )) assertThrows(IllegalArgumentException::class.java) { LogoImages.checkSvg(bad.toByteArray()) }
    }

    @Test
    fun `custom logo, otherwise the site's, otherwise ThermoBox's`() {
        val dir = Files.createTempDirectory("logo")
        val site = Logo(png(64, 64), LogoImages.PNG, LogoSource.SITE)
        var asked = 0
        val logos = ServerLogo(dir, { s -> asked++; if (s == "example.com") site else null }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

        assertEquals(LogoSource.DEFAULT, logos.current("localhost").source)
        assertEquals(LogoSource.DEFAULT, logos.current("thermobox.other.org").source)
        assertEquals(LogoSource.SITE, logos.current("thermobox.example.com").source)
        logos.current("thermobox.example.com")
        assertEquals(2, asked, "a site is asked once, then kept")

        val custom = logos.setCustom(png(3000, 1500))
        assertEquals(LogoSource.CUSTOM, logos.current("thermobox.example.com").source)
        assertEquals(1024 to 512, size(custom.bytes))
        logos.setCustom("""<svg xmlns="http://www.w3.org/2000/svg"/>""".toByteArray())
        assertEquals(LogoImages.SVG, logos.current(null).type)
        assertThrows(IllegalArgumentException::class.java) { logos.setCustom("hello".toByteArray()) }
        assertEquals(LogoImages.SVG, logos.current(null).type, "a refused file keeps the previous logo")

        logos.deleteCustom()
        assertEquals(LogoSource.SITE, logos.current("thermobox.example.com").source)
        assertEquals(LogoSource.DEFAULT, logos.current(null).source)
    }

    @Test
    fun `the logo of a site is found on its home page`() {
        val icon = png(180, 180)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                val (status, body, location) = when (ex.requestURI.path) {
                    "/" -> Triple(301, ByteArray(0), "/home")
                    "/home" -> Triple(200, """<html><head><link rel="apple-touch-icon" href="img/apple.png"><link rel="icon" href="/missing.png" sizes="16x16"></head></html>""".toByteArray(), null)
                    "/img/apple.png" -> Triple(200, icon, null)
                    else -> Triple(404, ByteArray(0), null)
                }
                location?.let { ex.responseHeaders.add("Location", it) }
                ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
            }
            start()
        }
        try {
            val logo = SiteLogo(allowLocal = true).fetch("127.0.0.1:${server.address.port}")
            assertEquals(LogoImages.PNG, logo?.type)
            assertTrue(logo!!.bytes.contentEquals(icon))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `without any icon, the site gives nothing`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
            start()
        }
        try {
            assertNull(SiteLogo(allowLocal = true).fetch("127.0.0.1:${server.address.port}"))
        } finally {
            server.stop(0)
        }
    }
}
