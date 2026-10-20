package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artefact
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
data class ReleaseGitHub(
    val tag: String,
    val titre: String,
    val notes: String,
    val prerelease: Boolean,
    val brouillon: Boolean,
    val publieeLe: Instant?,
    val page: String,
    val artefacts: List<Artefact>,
)

/** GitHub REST API, read only: `GET /repos/{depot}/releases`. */
class GitHubReleases(private val config: ConfigMisesAJour, private val http: HttpClient = client()) {

    fun lister(): List<ReleaseGitHub> {
        val requete = HttpRequest.newBuilder(URI.create("${config.apiGitHub}/repos/${config.depot}/releases?per_page=50"))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ThermoBox/${BuildInfo.texte}")
            .apply { config.jetonGitHub?.let { header("Authorization", "Bearer $it") } }
            .GET().build()
        val reponse = try {
            http.send(requete, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw IOException("GitHub injoignable (${e.message ?: e.javaClass.simpleName})", e)
        }
        when (reponse.statusCode()) {
            200 -> return analyser(reponse.body())
            404 -> throw IOException("Dépôt GitHub « ${config.depot} » introuvable")
            403, 429 -> throw IOException(limite(reponse))
            else -> throw IOException("GitHub a répondu ${reponse.statusCode()}")
        }
    }

    private fun limite(reponse: HttpResponse<String>): String {
        val reset = reponse.headers().firstValue("x-ratelimit-reset").orElse(null)?.toLongOrNull()
            ?: return "GitHub refuse la requête (${reponse.statusCode()})"
        val heure = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(reset))
        return "Limite de requêtes GitHub atteinte, réessayez après $heure (ou définissez THERMOBOX_GITHUB_TOKEN)"
    }

    companion object {
        private val json = ObjectMapper()

        fun client(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // Redirects are followed by hand by Telechargeur, which checks every host.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        fun analyser(corps: String): List<ReleaseGitHub> =
            json.readTree(corps).map { r ->
                ReleaseGitHub(
                    tag = r.texte("tag_name"),
                    titre = r.texte("name").ifBlank { r.texte("tag_name") },
                    notes = r.texte("body"),
                    prerelease = r.path("prerelease").asBoolean(false),
                    brouillon = r.path("draft").asBoolean(false),
                    publieeLe = r.texte("published_at").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    page = r.texte("html_url"),
                    artefacts = r.path("assets").map { a ->
                        Artefact(
                            nom = a.texte("name"),
                            url = a.texte("browser_download_url"),
                            taille = a.path("size").asLong(0),
                            // "sha256:<hex>", published by GitHub for every asset since 2025.
                            sha256 = a.texte("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                        )
                    },
                )
            }

        private fun JsonNode.texte(champ: String): String = path(champ).takeUnless { it.isNull }?.asText("") ?: ""
    }
}

/**
 * What the release notes say about the risks. Convention for the authors of a release: a
 * "## Migration" section (or lines starting with "Migration :") lists the operations to do,
 * the word "incompatible" (or "breaking change") flags an incompatible version.
 */
object NotesDeVersion {
    private val TITRE = Regex("""^(#{1,6})\s*(.*)$""")
    private val TITRE_MIGRATION = Regex("""^(migrations?|mise à niveau|mise a niveau|base de données|base de donnees|upgrade)\b""", RegexOption.IGNORE_CASE)
    private val LIGNE_MIGRATION = Regex("""^\s*[-*]?\s*migration\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val INCOMPATIBLE = Regex("""\bincompatibles?\b|\bbreaking changes?\b""", RegexOption.IGNORE_CASE)

    fun migration(notes: String): String? {
        val lignes = notes.lines()
        val section = StringBuilder()
        var niveau = 0
        for (ligne in lignes) {
            val titre = TITRE.matchEntire(ligne.trim())
            if (titre != null) {
                val n = titre.groupValues[1].length
                if (niveau > 0 && n <= niveau) niveau = 0
                if (niveau == 0 && TITRE_MIGRATION.containsMatchIn(titre.groupValues[2])) {
                    niveau = n
                    continue
                }
            }
            if (niveau > 0) section.appendLine(ligne)
            else LIGNE_MIGRATION.matchEntire(ligne)?.let { section.appendLine("- " + it.groupValues[1].trim()) }
        }
        return section.toString().trim().ifEmpty { null }
    }

    fun incompatible(notes: String): Boolean = INCOMPATIBLE.containsMatchIn(notes)
}
