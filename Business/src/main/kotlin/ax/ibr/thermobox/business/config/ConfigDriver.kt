package ax.ibr.thermobox.business.config

/**
 * Where measures come from, from the environment:
 * - `THERMOBOX_DRIVER`: `simulated` (default, fake devices) or `mqtt` (`simule`, the former
 *   value, is still accepted)
 * - `THERMOBOX_MQTT_URL` : broker, `tcp://localhost:1883` by default (`ssl://…` for TLS)
 * - `THERMOBOX_MQTT_USER` / `THERMOBOX_MQTT_PASSWORD` : optional broker credentials
 *
 * An unknown value stops the server at startup rather than silently falling back to fake data.
 */
data class ConfigDriver(
    val type: DriverType = DriverType.SIMULATED,
    val mqttUrl: String = MQTT_URL,
    val mqttUser: String? = null,
    val mqttPassword: String? = null,
) {
    enum class DriverType { SIMULATED, MQTT }

    init {
        require(Regex("""(tcp|ssl|ws|wss)://[^\s/]+(:\d+)?/?""").matches(mqttUrl)) {
            "Invalid THERMOBOX_MQTT_URL: $mqttUrl (expected tcp://host:port or ssl://host:port)"
        }
    }

    companion object {
        const val MQTT_URL = "tcp://localhost:1883"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): ConfigDriver {
            fun v(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            val type = when (v("THERMOBOX_DRIVER")?.lowercase()) {
                null, "simule", "simulé", "simulated", "simulation" -> DriverType.SIMULATED
                "mqtt" -> DriverType.MQTT
                else -> throw IllegalArgumentException("Invalid THERMOBOX_DRIVER: ${v("THERMOBOX_DRIVER")} (simulated or mqtt)")
            }
            return ConfigDriver(
                type = type,
                mqttUrl = v("THERMOBOX_MQTT_URL") ?: MQTT_URL,
                mqttUser = v("THERMOBOX_MQTT_USER"),
                mqttPassword = env["THERMOBOX_MQTT_PASSWORD"]?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
