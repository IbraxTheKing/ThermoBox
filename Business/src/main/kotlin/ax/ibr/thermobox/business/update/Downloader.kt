package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/**
 * Downloads release assets. Integrity: the SHA-256 of the file must match the digest GitHub
 * publishes for the asset (fetched over HTTPS from api.github.com). Every hop of the redirect
 * chain must be HTTPS and on a GitHub host, and the GitHub token is never sent along.
 */
class Downloader(private val config: UpdateConfig, private val http: HttpClient = GitHubReleases.client()) {

    fun download(artifact: Artifact, target: Path, progress: (Long) -> Unit = {}) {
        val expected = artifact.sha256 ?: throw IOException("GitHub publishes no SHA-256 digest for ${artifact.name}")
        if (artifact.size > config.maxArtifactSize) throw IOException("${artifact.name} exceeds the allowed size")

        var uri = checkHost(URI.create(artifact.url))
        repeat(MAX_REDIRECTS + 1) {
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "ThermoBox/${BuildInfo.text}")
                .header("Accept", "application/octet-stream")
                .GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            when (response.statusCode()) {
                in 300..399 -> {
                    response.body().close()
                    val next = response.headers().firstValue("location").orElseThrow { IOException("Redirect without a location") }
                    uri = checkHost(uri.resolve(next))
                }
                200 -> {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    response.body().use { entry ->
                        Files.newOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = entry.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > config.maxArtifactSize) throw IOException("${artifact.name} exceeds the allowed size")
                                digest.update(buffer, 0, n)
                                output.write(buffer, 0, n)
                                progress(total)
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != expected) {
                        Files.deleteIfExists(target)
                        throw IOException("SHA-256 digest of ${artifact.name} is wrong: the file is tampered with or incomplete")
                    }
                    return
                }
                else -> {
                    response.body().close()
                    throw IOException("Download of ${artifact.name} refused (HTTP ${response.statusCode()})")
                }
            }
        }
        throw IOException("Too many redirects for ${artifact.name}")
    }

    private fun checkHost(uri: URI): URI {
        val host = uri.host?.lowercase() ?: throw IOException("Invalid download address")
        val local = config.allowLocalHttp && uri.scheme == "http" && host == "127.0.0.1"
        if (!local && (uri.scheme != "https" || host !in config.downloadHosts)) {
            throw IOException("Download refused from $host: only GitHub is allowed")
        }
        return uri
    }

    companion object {
        private const val MAX_REDIRECTS = 5

        fun sha256(file: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { entry ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = entry.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
