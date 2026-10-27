package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Canal
import java.nio.file.Path
import java.time.Duration

/**
 * Settings of the update system, from the environment:
 * - `THERMOBOX_UPDATE_REPO` : GitHub repository (IbraxTheKing/ThermoBox)
 * - `THERMOBOX_UPDATE_CHANNEL` : `stable` (default) or `prerelease` (nightly, beta, rc too)
 * - `THERMOBOX_UPDATE_CHECK` : `false` turns the daily check off
 * - `THERMOBOX_DATA_DIR` : folder shared by REST and JSF (downloads, backups), ~/.thermobox
 * - `THERMOBOX_GITHUB_TOKEN` : optional, raises the GitHub API limit (60 requests/hour without)
 */
data class ConfigMisesAJour(
    val depot: String = DEPOT,
    val canal: Canal = Canal.STABLE,
    val verificationAutomatique: Boolean = true,
    val dossier: Path = Path.of(System.getProperty("user.home"), ".thermobox"),
    val jetonGitHub: String? = null,
    val apiGitHub: String = "https://api.github.com",
    /** Hosts a download may come from (GitHub redirects release assets to its CDN). */
    val hotesTelechargement: Set<String> = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
    /** Tests only: plain http to 127.0.0.1. */
    val httpLocalAutorise: Boolean = false,
    val intervalleVerification: Duration = Duration.ofDays(1),
    val tailleMaxArtefact: Long = 300L * 1024 * 1024,
) {
    init {
        require(Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""").matches(depot)) { "THERMOBOX_UPDATE_REPO invalide : $depot" }
    }

    companion object {
        const val DEPOT = "IbraxTheKing/ThermoBox"

        fun depuisEnvironnement(env: Map<String, String> = System.getenv()): ConfigMisesAJour {
            fun v(nom: String) = env[nom]?.trim()?.takeIf { it.isNotEmpty() }
            return ConfigMisesAJour(
                depot = v("THERMOBOX_UPDATE_REPO") ?: DEPOT,
                canal = if (v("THERMOBOX_UPDATE_CHANNEL").equals("prerelease", ignoreCase = true)) Canal.PRERELEASE else Canal.STABLE,
                verificationAutomatique = !v("THERMOBOX_UPDATE_CHECK").equals("false", ignoreCase = true),
                dossier = v("THERMOBOX_DATA_DIR")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".thermobox"),
                jetonGitHub = v("THERMOBOX_GITHUB_TOKEN"),
            )
        }
    }
}
