package ax.ibr.thermobox.restserver.branding

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * The logo of the website a ThermoBox server belongs to: a server reached at
 * `thermobox.example.com` shows the logo of `example.com`.
 *
 * The home page of the site is read for its icons (`<link rel="apple-touch-icon">`,
 * `<link rel="icon">`, the largest first), then `/favicon.ico`. The host comes from the request,
 * so the requests are kept harmless: HTTPS only, public addresses only (also after a redirect),
 * small and short downloads.
 */
class SiteLogo(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    /** Tests only: plain HTTP and local addresses. */
    private val allowLocal: Boolean = false,
) {

    /** The image found for [site] (a domain such as `example.com`), or null. */
    fun fetch(site: String): Logo? {
        val home = URI.create("${if (allowLocal) "http" else "https"}://$site/")
        val page = runCatching { get(home, MAX_PAGE_BYTES) }.getOrNull()
        val candidates = ArrayList<URI>()
        if (page != null) {
            val html = String(page.second, Charsets.UTF_8)
            candidates += icons(html).mapNotNull { href -> runCatching { page.first.resolve(href.trim()) }.getOrNull() }
        }
        candidates += (page?.first ?: home).resolve("/favicon.ico")
        for (uri in candidates.distinct().take(MAX_CANDIDATES)) {
            val bytes = when (uri.scheme) {
                "data" -> dataUri(uri.toString())
                else -> runCatching { get(uri, MAX_IMAGE_BYTES).second }.getOrNull()
            } ?: continue
            val type = LogoImages.sniff(bytes) ?: continue
            if (LogoImages.isRaster(type) && runCatching { LogoImages.toPng(bytes, 16) }.isFailure) continue
            if (type == LogoImages.SVG && runCatching { LogoImages.checkSvg(bytes) }.isFailure) continue
            return Logo(bytes, type, LogoSource.SITE)
        }
        return null
    }

    /** GET with manual redirects, every hop checked; the final URI and the body. */
    private fun get(start: URI, maxBytes: Int): Pair<URI, ByteArray> {
        var uri = start
        repeat(MAX_REDIRECTS + 1) {
            checkTarget(uri)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "ThermoBox")
                .header("Accept", "text/html,image/*;q=0.9,*/*;q=0.5")
                .GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            when (response.statusCode()) {
                in 200..299 -> return uri to response.body().use { read(it, maxBytes) }
                301, 302, 303, 307, 308 -> {
                    response.body().close()
                    val location = response.headers().firstValue("Location").orElse(null)
                        ?: throw IllegalStateException("Redirect without a location")
                    uri = uri.resolve(location)
                }
                else -> {
                    response.body().close()
                    throw IllegalStateException("HTTP ${response.statusCode()}")
                }
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun checkTarget(uri: URI) {
        val host = uri.host ?: throw IllegalStateException("No host")
        if (allowLocal) return
        require(uri.scheme == "https" && (uri.port == -1 || uri.port == 443)) { "HTTPS on port 443 only" }
        require(InetAddress.getAllByName(host).all { isPublic(it) }) { "$host is not a public address" }
    }

    private fun read(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            // A page is only read for its <head>; an image must fit entirely
            if (out.size() >= maxBytes) {
                if (maxBytes == MAX_PAGE_BYTES) break
                throw IllegalStateException("Too large")
            }
        }
        return out.toByteArray()
    }

    private fun dataUri(uri: String): ByteArray? {
        val m = Regex("""^data:image/[\w.+-]+;base64,(.+)$""", RegexOption.DOT_MATCHES_ALL).find(uri) ?: return null
        return runCatching { Base64.getDecoder().decode(m.groupValues[1].trim()) }.getOrNull()?.takeIf { it.size <= MAX_IMAGE_BYTES }
    }

    companion object {
        private const val MAX_PAGE_BYTES = 512 * 1024
        private const val MAX_IMAGE_BYTES = 1024 * 1024
        private const val MAX_REDIRECTS = 4
        private const val MAX_CANDIDATES = 6

        /**
         * The site of a server reached at [host]: `thermobox.example.com` → `example.com`.
         * Null for an address, `localhost`, a name without a dot or a local name.
         */
        fun siteOf(host: String?): String? {
            val h = host?.trim()?.lowercase()?.removeSuffix(".")?.takeIf { it.isNotEmpty() } ?: return null
            if (h.startsWith("[") || h.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || ':' in h) return null
            if (!h.matches(Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+"""))) return null
            if (h == "localhost" || LOCAL_SUFFIXES.any { h.endsWith(it) }) return null
            val labels = h.split('.')
            return if (labels.size > 2) labels.drop(1).joinToString(".") else h
        }

        private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".internal", ".lan", ".home", ".corp", ".test", ".invalid", ".example")

        /** Icons declared by a home page, the largest first (apple-touch-icon counts as 180 px). */
        internal fun icons(html: String): List<String> {
            val found = ArrayList<Pair<Int, String>>()
            for (tag in Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
                fun attr(name: String) = Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
                    .find(tag.value)?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] } }
                val rel = attr("rel")?.lowercase()?.split(Regex("""\s+""")) ?: continue
                val href = attr("href")?.takeIf { it.isNotBlank() } ?: continue
                val score = when {
                    "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel -> 180
                    "icon" in rel -> attr("sizes")?.lowercase()?.let { s ->
                        if (s == "any") 512 else Regex("""(\d+)x\d+""").findAll(s).maxOfOrNull { it.groupValues[1].toInt() }
                    } ?: 32
                    else -> continue
                }
                found += score to href.replace("&amp;", "&")
            }
            return found.sortedByDescending { it.first }.map { it.second }
        }

        /** Not loopback, private, link-local, multicast, carrier-grade NAT nor unique local IPv6. */
        internal fun isPublic(a: InetAddress): Boolean {
            if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
            val b = a.address
            if (a is Inet4Address) {
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                if (first == 0 || first >= 240) return false
                if (first == 100 && second in 64..127) return false
                if (first == 192 && second == 0 && (b[2].toInt() and 0xFF) == 0) return false
            }
            if (a is Inet6Address && (b[0].toInt() and 0xFE) == 0xFC) return false
            return true
        }
    }
}
