package ax.ibr.thermobox.business.config

/**
 * Where measures come from, from the environment:
 * - `THERMOBOX_DRIVER` : `simule` (default, fake boxes) or `mqtt`
 * - `THERMOBOX_MQTT_URL` : broker, `tcp://localhost:1883` by default (`ssl://…` for TLS)
 * - `THERMOBOX_MQTT_USER` / `THERMOBOX_MQTT_PASSWORD` : optional broker credentials
 *
 * An unknown value stops the server at startup rather than silently falling back to fake data.
 */
data class ConfigDriver(
    val type: TypeDriver = TypeDriver.SIMULE,
    val urlMqtt: String = URL_MQTT,
    val utilisateurMqtt: String? = null,
    val motDePasseMqtt: String? = null,
) {
    enum class TypeDriver { SIMULE, MQTT }

    init {
        require(Regex("""(tcp|ssl|ws|wss)://[^\s/]+(:\d+)?/?""").matches(urlMqtt)) {
            "THERMOBOX_MQTT_URL invalide : $urlMqtt (attendu tcp://hôte:port ou ssl://hôte:port)"
        }
    }

    companion object {
        const val URL_MQTT = "tcp://localhost:1883"

        fun depuisEnvironnement(env: Map<String, String> = System.getenv()): ConfigDriver {
            fun v(nom: String) = env[nom]?.trim()?.takeIf { it.isNotEmpty() }
            val type = when (v("THERMOBOX_DRIVER")?.lowercase()) {
                null, "simule", "simulé", "simulated", "simulation" -> TypeDriver.SIMULE
                "mqtt" -> TypeDriver.MQTT
                else -> throw IllegalArgumentException("THERMOBOX_DRIVER invalide : ${v("THERMOBOX_DRIVER")} (simule ou mqtt)")
            }
            return ConfigDriver(
                type = type,
                urlMqtt = v("THERMOBOX_MQTT_URL") ?: URL_MQTT,
                utilisateurMqtt = v("THERMOBOX_MQTT_USER"),
                motDePasseMqtt = env["THERMOBOX_MQTT_PASSWORD"]?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
