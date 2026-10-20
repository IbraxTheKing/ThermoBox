package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artefact
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
class Telechargeur(private val config: ConfigMisesAJour, private val http: HttpClient = GitHubReleases.client()) {

    fun telecharger(artefact: Artefact, cible: Path, progression: (Long) -> Unit = {}) {
        val attendu = artefact.sha256 ?: throw IOException("GitHub ne publie pas d'empreinte SHA-256 pour ${artefact.nom}")
        if (artefact.taille > config.tailleMaxArtefact) throw IOException("${artefact.nom} dépasse la taille autorisée")

        var uri = verifierHote(URI.create(artefact.url))
        repeat(MAX_REDIRECTIONS + 1) {
            val requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "ThermoBox/${BuildInfo.texte}")
                .header("Accept", "application/octet-stream")
                .GET().build()
            val reponse = http.send(requete, HttpResponse.BodyHandlers.ofInputStream())
            when (reponse.statusCode()) {
                in 300..399 -> {
                    reponse.body().close()
                    val suivante = reponse.headers().firstValue("location").orElseThrow { IOException("Redirection sans adresse") }
                    uri = verifierHote(uri.resolve(suivante))
                }
                200 -> {
                    val empreinte = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    reponse.body().use { entree ->
                        Files.newOutputStream(cible).use { sortie ->
                            val tampon = ByteArray(64 * 1024)
                            while (true) {
                                val n = entree.read(tampon)
                                if (n < 0) break
                                total += n
                                if (total > config.tailleMaxArtefact) throw IOException("${artefact.nom} dépasse la taille autorisée")
                                empreinte.update(tampon, 0, n)
                                sortie.write(tampon, 0, n)
                                progression(total)
                            }
                        }
                    }
                    val obtenu = empreinte.digest().joinToString("") { "%02x".format(it) }
                    if (obtenu != attendu) {
                        Files.deleteIfExists(cible)
                        throw IOException("Empreinte SHA-256 de ${artefact.nom} incorrecte : fichier altéré ou incomplet")
                    }
                    return
                }
                else -> {
                    reponse.body().close()
                    throw IOException("Téléchargement de ${artefact.nom} refusé (HTTP ${reponse.statusCode()})")
                }
            }
        }
        throw IOException("Trop de redirections pour ${artefact.nom}")
    }

    private fun verifierHote(uri: URI): URI {
        val hote = uri.host?.lowercase() ?: throw IOException("Adresse de téléchargement invalide")
        val local = config.httpLocalAutorise && uri.scheme == "http" && hote == "127.0.0.1"
        if (!local && (uri.scheme != "https" || hote !in config.hotesTelechargement)) {
            throw IOException("Téléchargement refusé depuis $hote : seul GitHub est autorisé")
        }
        return uri
    }

    companion object {
        private const val MAX_REDIRECTIONS = 5

        fun sha256(fichier: Path): String {
            val empreinte = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(fichier).use { entree ->
                val tampon = ByteArray(64 * 1024)
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    empreinte.update(tampon, 0, n)
                }
            }
            return empreinte.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
