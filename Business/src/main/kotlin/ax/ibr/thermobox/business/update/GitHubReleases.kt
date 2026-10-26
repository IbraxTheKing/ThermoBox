package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A release as GitHub describes it, before it is compared with the installed version. */
data class GitHubRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: Instant?,
    val page: String,
    val artifacts: List<Artifact>,
)

/** GitHub REST API, read only: `GET /repos/{repository}/releases`. */
class GitHubReleases(private val config: UpdateConfig, private val http: HttpClient = client()) {

    fun list(): List<GitHubRelease> {
        val request = HttpRequest.newBuilder(URI.create("${config.gitHubApi}/repos/${config.repository}/releases?per_page=50"))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ThermoBox/${BuildInfo.text}")
            .apply { config.gitHubToken?.let { header("Authorization", "Bearer $it") } }
            .GET().build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw IOException("GitHub unreachable (${e.message ?: e.javaClass.simpleName})", e)
        }
        when (response.statusCode()) {
            200 -> return analyze(response.body())
            404 -> throw IOException("GitHub repository \"${config.repository}\" not found")
            403, 429 -> throw IOException(rateLimitMessage(response))
            else -> throw IOException("GitHub answered ${response.statusCode()}")
        }
    }

    private fun rateLimitMessage(response: HttpResponse<String>): String {
        val reset = response.headers().firstValue("x-ratelimit-reset").orElse(null)?.toLongOrNull()
            ?: return "GitHub refuses the request (${response.statusCode()})"
        val hour = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(reset))
        return "GitHub request limit reached, try again after $hour (or set THERMOBOX_GITHUB_TOKEN)"
    }

    companion object {
        private val json = ObjectMapper()

        fun client(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // Redirects are followed by hand by Downloader, which checks every host.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        fun analyze(body: String): List<GitHubRelease> =
            json.readTree(body).map { r ->
                GitHubRelease(
                    tag = r.text("tag_name"),
                    title = r.text("name").ifBlank { r.text("tag_name") },
                    notes = r.text("body"),
                    prerelease = r.path("prerelease").asBoolean(false),
                    draft = r.path("draft").asBoolean(false),
                    publishedAt = r.text("published_at").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    page = r.text("html_url"),
                    artifacts = r.path("assets").map { a ->
                        Artifact(
                            name = a.text("name"),
                            url = a.text("browser_download_url"),
                            size = a.path("size").asLong(0),
                            // "sha256:<hex>", published by GitHub for every asset since 2025.
                            sha256 = a.text("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                        )
                    },
                )
            }

        private fun JsonNode.text(field: String): String = path(field).takeUnless { it.isNull }?.asText("") ?: ""
    }
}

/**
 * What the release notes say about the risks. Convention for the authors of a release: a
 * "## Migration" section (or lines starting with "Migration :") lists the operations to do,
 * the word "incompatible" (or "breaking change") flags an incompatible version.
 */
object ReleaseNotes {
    private val TITLE = Regex("""^(#{1,6})\s*(.*)$""")
    // The French headings of older release notes are still recognized.
    private val MIGRATION_TITLE = Regex("""^(migrations?|upgrade|database|mise à niveau|mise a niveau|base de données|base de donnees)\b""", RegexOption.IGNORE_CASE)
    private val MIGRATION_LINE = Regex("""^\s*[-*]?\s*migration\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val INCOMPATIBLE = Regex("""\bincompatibles?\b|\bbreaking changes?\b""", RegexOption.IGNORE_CASE)

    fun migration(notes: String): String? {
        val lines = notes.lines()
        val section = StringBuilder()
        var level = 0
        for (line in lines) {
            val title = TITLE.matchEntire(line.trim())
            if (title != null) {
                val n = title.groupValues[1].length
                if (level > 0 && n <= level) level = 0
                if (level == 0 && MIGRATION_TITLE.containsMatchIn(title.groupValues[2])) {
                    level = n
                    continue
                }
            }
            if (level > 0) section.appendLine(line)
            else MIGRATION_LINE.matchEntire(line)?.let { section.appendLine("- " + it.groupValues[1].trim()) }
        }
        return section.toString().trim().ifEmpty { null }
    }

    fun incompatible(notes: String): Boolean = INCOMPATIBLE.containsMatchIn(notes)
}
