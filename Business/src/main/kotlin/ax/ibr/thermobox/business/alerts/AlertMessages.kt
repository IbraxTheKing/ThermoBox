package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.DeliveryKind
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A message ready to be sent: [subject] (email subject, embed title), [text] (body), [values] (placeholders). */
data class AlertMessage(
    val kind: DeliveryKind,
    val subject: String,
    val text: String,
    val event: AlertEvent?,
    val values: Map<String, String>,
    val sentAt: LocalDateTime,
)

/**
 * The texts of the alerts, in French (the people who receive them read the interface in French).
 *
 * A channel may replace the text by its own template, with these placeholders: {type} (Dépassement,
 * Rappel, Retour à la normale, Test), {room}, {place}, {temperature}, {peak}, {setpoint},
 * {threshold}, {since}, {duration}, {devices}, {rule}, {channel}, {date}. An unknown placeholder
 * stays as it is.
 */
object AlertMessages {

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val PLACEHOLDER = Regex("""\{(\w+)}""")

    fun of(kind: DeliveryKind, event: AlertEvent?, channelName: String, now: LocalDateTime, template: String? = null): AlertMessage {
        val values = values(kind, event, channelName, now)
        val room = values.getValue("room")
        val subject = when (kind) {
            DeliveryKind.TRIGGERED -> "[ThermoBox] Dépassement de température : $room (${values["temperature"]})"
            DeliveryKind.REMINDER -> "[ThermoBox] Rappel : dépassement toujours en cours, $room (${values["temperature"]})"
            DeliveryKind.RESOLVED -> "[ThermoBox] Retour à la normale : $room"
            DeliveryKind.TEST -> "[ThermoBox] Test du canal « $channelName »"
        }
        val text = template?.takeIf { it.isNotBlank() }?.let { render(it, values) } ?: defaultText(kind, event, values)
        return AlertMessage(kind, subject, text, event, values, now)
    }

    fun render(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { m -> values[m.groupValues[1]] ?: m.value }

    private fun defaultText(kind: DeliveryKind, event: AlertEvent?, v: Map<String, String>): String {
        if (event == null || kind == DeliveryKind.TEST) {
            return "Ce message de test confirme que le canal « ${v["channel"]} » fonctionne : les alertes de dépassement de " +
                "température de ThermoBox y arriveront.\n\nEnvoyé le ${v["date"]}."
        }
        val lines = ArrayList<String>()
        when (kind) {
            DeliveryKind.TRIGGERED -> lines += "La température de ${v["room"]} dépasse sa consigne."
            DeliveryKind.REMINDER -> lines += "Rappel : la température de ${v["room"]} dépasse toujours sa consigne."
            DeliveryKind.RESOLVED -> lines += "La température de ${v["room"]} est revenue sous le seuil."
            DeliveryKind.TEST -> Unit
        }
        lines += ""
        if (v["place"].orEmpty().isNotEmpty()) lines += "Lieu : ${v["place"]}"
        if (kind == DeliveryKind.RESOLVED) {
            lines += "Dernière mesure : ${v["temperature"]} (maximum atteint : ${v["peak"]})"
        } else {
            lines += "Température : ${v["temperature"]}" + if (kind == DeliveryKind.REMINDER) " (maximum atteint : ${v["peak"]})" else ""
        }
        lines += "Consigne : ${v["setpoint"]}, seuil d'alerte : ${v["threshold"]}"
        lines += when (kind) {
            DeliveryKind.RESOLVED -> "Dépassement du ${v["since"]} au ${v["date"]} (${v["duration"]})"
            else -> "Au-dessus du seuil depuis le ${v["since"]} (${v["duration"]})"
        }
        if (v["devices"].orEmpty().isNotEmpty()) lines += "Boîtiers : ${v["devices"]}"
        lines += "Règle : ${v["rule"]}"
        return lines.joinToString("\n")
    }

    private fun values(kind: DeliveryKind, e: AlertEvent?, channelName: String, now: LocalDateTime): Map<String, String> {
        val type = when (kind) {
            DeliveryKind.TRIGGERED -> "Dépassement"
            DeliveryKind.REMINDER -> "Rappel"
            DeliveryKind.RESOLVED -> "Retour à la normale"
            DeliveryKind.TEST -> "Test"
        }
        val base = mapOf("type" to type, "channel" to channelName, "date" to DATE.format(now))
        if (e == null) {
            return base + mapOf(
                "room" to "Salle de test", "place" to "Bâtiment de test", "temperature" to celsius(27.5f), "peak" to celsius(28f),
                "setpoint" to celsius(22f), "threshold" to celsius(23f), "since" to DATE.format(now.minusMinutes(15)),
                "duration" to duration(Duration.ofMinutes(15)), "devices" to "Boîtier de test ${celsius(27.5f)}", "rule" to "Test",
            )
        }
        val end = e.resolvedAt ?: now
        val current = if (kind == DeliveryKind.TRIGGERED) e.temperature else e.lastTemperature ?: e.temperature
        val setpoint = e.currentSetpoint ?: e.setpoint
        return base + mapOf(
            "room" to (e.roomName ?: "Salle ${e.roomId}"),
            "place" to e.place,
            "temperature" to celsius(current),
            "peak" to celsius(e.peak),
            "setpoint" to celsius(setpoint),
            "threshold" to celsius(e.threshold - e.setpoint + setpoint),
            "since" to DATE.format(e.exceededSince),
            "duration" to duration(Duration.between(e.exceededSince, end)),
            "devices" to e.devices.filter { it.exceeding || kind == DeliveryKind.RESOLVED }
                .joinToString(", ") { d -> (d.name ?: "Boîtier ${d.id}") + (d.temperature?.let { " " + celsius(it) } ?: "") },
            "rule" to e.ruleName,
        )
    }

    fun celsius(t: Float): String = String.format(Locale.FRANCE, "%.1f °C", t)

    /** « 1 h 05 », « 12 min ». */
    fun duration(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60} h %02d".format(minutes % 60)
            else -> "${minutes / (24 * 60)} j ${(minutes / 60) % 24} h"
        }
    }
}
