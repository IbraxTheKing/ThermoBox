package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Channel
import java.nio.file.Path
import java.time.Duration

/**
 * Settings of the update system, from the environment:
 * - `THERMOBOX_UPDATE_REPO` : GitHub repository (IbraxTheKing/ThermoBox)
 * - `THERMOBOX_UPDATE_CHANNEL` : `stable` (default) or `prerelease` (nightly, beta, rc too)
 * - `THERMOBOX_UPDATE_CHECK` : `false` turns the daily check off
 * - `THERMOBOX_DATA_DIR` : data folder (downloads, backups, settings), ~/.thermobox
 * - `THERMOBOX_GITHUB_TOKEN` : optional, raises the GitHub API limit (60 requests/hour without)
 */
data class UpdateConfig(
    val repository: String = REPOSITORY,
    val channel: Channel = Channel.STABLE,
    val automaticCheck: Boolean = true,
    val directory: Path = Path.of(System.getProperty("user.home"), ".thermobox"),
    val gitHubToken: String? = null,
    val gitHubApi: String = "https://api.github.com",
    /** Hosts a download may come from (GitHub redirects release assets to its CDN). */
    val downloadHosts: Set<String> = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
    /** Tests only: plain http to 127.0.0.1. */
    val allowLocalHttp: Boolean = false,
    val checkInterval: Duration = Duration.ofDays(1),
    val maxArtifactSize: Long = 300L * 1024 * 1024,
) {
    init {
        require(Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""").matches(repository)) { "Invalid THERMOBOX_UPDATE_REPO: $repository" }
    }

    companion object {
        const val REPOSITORY = "IbraxTheKing/ThermoBox"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): UpdateConfig {
            fun v(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            return UpdateConfig(
                repository = v("THERMOBOX_UPDATE_REPO") ?: REPOSITORY,
                channel = if (v("THERMOBOX_UPDATE_CHANNEL").equals("prerelease", ignoreCase = true)) Channel.PRERELEASE else Channel.STABLE,
                automaticCheck = !v("THERMOBOX_UPDATE_CHECK").equals("false", ignoreCase = true),
                directory = v("THERMOBOX_DATA_DIR")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".thermobox"),
                gitHubToken = v("THERMOBOX_GITHUB_TOKEN"),
            )
        }
    }
}
