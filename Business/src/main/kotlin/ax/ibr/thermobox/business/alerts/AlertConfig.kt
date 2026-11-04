package ax.ibr.thermobox.business.alerts

import java.nio.file.Path
import java.time.Duration

/**
 * Settings of the temperature alerts that belong to the machine, not to the administrators (those
 * are in the database, managed through /api/v2/alerts):
 *
 * - `THERMOBOX_ALERTS`: `false` stops this server from watching the rooms and sending (another
 *   server sharing the database may still do it); `true` by default.
 * - `THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS`: `true` lets webhooks reach addresses of the local
 *   network (an internal service); refused by default, so that an ADMIN account cannot make the
 *   server call what only it can reach.
 * - `THERMOBOX_SECRETS_KEY`: key of the stored secrets (32 bytes in Base64); otherwise
 *   `secrets.key` in the data folder, created on first use.
 * - `THERMOBOX_DATA_DIR`: the data folder (~/.thermobox).
 */
data class AlertConfig(
    val enabled: Boolean = true,
    val allowPrivateNetworks: Boolean = false,
    val dataDirectory: Path = Path.of(System.getProperty("user.home"), ".thermobox"),
    val secretsKey: String? = null,
    /** How often the rooms are checked and the messages due are sent. */
    val interval: Duration = Duration.ofSeconds(30),
    /** Ended alerts older than this are deleted. */
    val history: Duration = Duration.ofDays(365),
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AlertConfig {
            fun v(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun flag(name: String, default: Boolean): Boolean = when (v(name)?.lowercase()) {
                null -> default
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> error("$name=${v(name)} is not valid: true or false")
            }
            return AlertConfig(
                enabled = flag("THERMOBOX_ALERTS", true),
                allowPrivateNetworks = flag("THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS", false),
                dataDirectory = v("THERMOBOX_DATA_DIR")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".thermobox"),
                secretsKey = v("THERMOBOX_SECRETS_KEY"),
            )
        }
    }
}
