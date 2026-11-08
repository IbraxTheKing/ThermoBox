package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One alert in a message: what happened ([kind]) to which [event], and its placeholders. */
data class AlertItem(val kind: DeliveryKind, val event: AlertEvent?, val values: Map<String, String>)

/**
 * A message ready to be sent: [subject] (email subject, embed title), [text] (body), the alerts it
 * tells ([items], several rooms at once), and which part it is when they did not fit in one.
 * [kind] is the most urgent of its items (an excess before a reminder before a resolution).
 */
data class AlertMessage(
    val kind: DeliveryKind,
    val subject: String,
    val text: String,
    val items: List<AlertItem>,
    val sentAt: LocalDateTime,
    val part: Int = 1,
    val parts: Int = 1,
    /** Written by the template of the channel: sent as it is, no layout of its own (Discord, Slack, HTML). */
    val templated: Boolean = false,
) {
    /** The alert of a message about one room (null for a test, or several rooms). */
    val event: AlertEvent? get() = items.singleOrNull()?.event
    /** The placeholders of its first alert. */
    val values: Map<String, String> get() = items.firstOrNull()?.values.orEmpty()
}

/** How much a message may hold on a channel: alerts, and characters of its text. */
data class MessageLimits(val items: Int, val chars: Int) {
    companion object {
        /** Discord: 4096 characters in an embed, 6000 in a message; kept short to be read. */
        val DISCORD = MessageLimits(25, 3800)
        /** Slack and those that read its {"text"}: past 4000 characters it is cut or refused. */
        val SLACK = MessageLimits(40, 3800)
        val GENERIC = MessageLimits(100, 60_000)
        val EMAIL = MessageLimits(100, 100_000)

        fun of(channel: AlertChannel): MessageLimits = when (channel.kind) {
            ChannelKind.EMAIL -> EMAIL
            ChannelKind.WEBHOOK -> when (runCatching { ChannelJson.webhook(channel.settings).format }.getOrDefault(WebhookFormat.GENERIC)) {
                WebhookFormat.DISCORD -> DISCORD
                WebhookFormat.SLACK -> SLACK
                WebhookFormat.GENERIC -> GENERIC
            }
        }
    }
}

/**
 * The texts of the alerts, in French (the people who receive them read the interface in French).
 *
 * The alerts due on a channel during one run go in one message ([digest]): every room that went
 * over, is still over (reminder) or came back, rather than a message per room. Past what the
 * channel takes ([MessageLimits]), they are split into several messages, « (1/2) », « (2/2) ».
 * A message about one room keeps its detailed text.
 *
 * In a message about several rooms, each room takes two short lines ([roomLine]): its name and
 * temperature against the threshold, then where it is and since when. The rule is told once when
 * every room has the same. Discord, Slack and email lay them out themselves (WebhookSender,
 * AlertHtml) from [sections] and [roomLine].
 *
 * A channel may replace the text by its own template, with these placeholders: {type} (Dépassement,
 * Rappel, Retour à la normale, Test), {room}, {place}, {temperature}, {peak}, {setpoint},
 * {threshold}, {since}, {duration}, {devices}, {rule}, {channel}, {date}. In a message about
 * several rooms, the template is written once per room. An unknown placeholder stays as it is.
 */
object AlertMessages {

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
    private val DAY = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)
    private val PLACEHOLDER = Regex("""\{(\w+)}""")
    /** The order of the sections, the most urgent first. */
    private val ORDER = listOf(DeliveryKind.TRIGGERED, DeliveryKind.REMINDER, DeliveryKind.RESOLVED, DeliveryKind.TEST)

    /** An alert to put in a message, with what it stands for ([key]: its delivery). */
    data class Entry<K>(val key: K, val kind: DeliveryKind, val event: AlertEvent?)

    /** A message about one alert (or a test). */
    fun of(kind: DeliveryKind, event: AlertEvent?, channelName: String, now: LocalDateTime, template: String? = null): AlertMessage =
        compose(listOf(AlertItem(kind, event, values(kind, event, channelName, now))), now, template, 1, 1)

    /**
     * The messages of [entries] for one channel: as few as [limits] allow, each with the keys of
     * the alerts it holds. Most urgent first, then by place and room.
     */
    fun <K> digest(entries: List<Entry<K>>, channelName: String, now: LocalDateTime, limits: MessageLimits, template: String? = null): List<Pair<AlertMessage, List<K>>> {
        if (entries.isEmpty()) return emptyList()
        val items = entries
            .map { it.key to AlertItem(it.kind, it.event, values(it.kind, it.event, channelName, now)) }
            .sortedWith(compareBy({ ORDER.indexOf(it.second.kind) }, { it.second.values["place"].orEmpty() }, { it.second.values["room"].orEmpty() }))
        if (items.size == 1) return listOf(compose(items.map { it.second }, now, template, 1, 1) to items.map { it.first })
        // Parts: as many alerts as fit, by the size of their line (a part always holds one)
        val parts = ArrayList<List<Pair<K, AlertItem>>>()
        var current = ArrayList<Pair<K, AlertItem>>()
        var size = HEADER
        for (pair in items) {
            val n = line(pair.second, template).length + SECTION
            if (current.isNotEmpty() && (current.size >= limits.items || size + n > limits.chars)) {
                parts += current
                current = ArrayList()
                size = HEADER
            }
            current += pair
            size += n
        }
        parts += current
        return parts.mapIndexed { i, part ->
            compose(part.map { it.second }, now, template, i + 1, parts.size, limits.chars) to part.map { it.first }
        }
    }

    fun render(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { m -> values[m.groupValues[1]] ?: m.value }

    /** The template of [channel], if it has one. */
    fun templateOf(channel: AlertChannel): String? = runCatching {
        when (channel.kind) {
            ChannelKind.EMAIL -> ChannelJson.email(channel.settings).template
            ChannelKind.WEBHOOK -> ChannelJson.webhook(channel.settings).template
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun compose(items: List<AlertItem>, now: LocalDateTime, template: String?, part: Int, parts: Int, maxChars: Int = Int.MAX_VALUE): AlertMessage {
        val kind = items.minOf { ORDER.indexOf(it.kind) }.let { ORDER[it] }
        val suffix = if (parts > 1) " ($part/$parts)" else ""
        val templated = template?.isNotBlank() == true
        if (items.size == 1) {
            val one = items.single()
            val text = if (templated) render(template!!, one.values) else defaultText(one.kind, one.event, one.values)
            return AlertMessage(kind, subject(one) + suffix, text, items, now, part, parts, templated)
        }
        val rule = sharedRule(items)
        val lines = ArrayList<String>()
        lines += summary(items) + "."
        for ((k, of) in sections(items)) {
            lines += ""
            lines += "${section(k)} (${of.size})"
            of.forEach { item ->
                lines += ""
                lines += if (templated) render(template!!, item.values) else roomLine(item, now, rule == null).let { "• ${it.title}\n  ${it.detail}" }
            }
        }
        lines += ""
        if (rule != null && !templated) lines += "Règle : $rule"
        lines += "Envoyé le ${DATE.format(now)}."
        return AlertMessage(kind, "[ThermoBox] ${summary(items)}$suffix", lines.joinToString("\n").take(maxChars), items, now, part, parts, templated)
    }

    /** The alerts of a message by section, the most urgent first. */
    fun sections(items: List<AlertItem>): List<Pair<DeliveryKind, List<AlertItem>>> =
        ORDER.mapNotNull { k -> items.filter { it.kind == k }.takeIf { it.isNotEmpty() }?.let { k to it } }

    /** The rule of every alert of the message, when they all have the same (told once). */
    fun sharedRule(items: List<AlertItem>): String? = items.map { it.values["rule"].orEmpty() }.distinct().singleOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * A room in a message about several: [title] « Salle 104 — 19,6 °C (seuil 19,0 °C) », [detail]
     * where and since when; [note] the threshold (the maximum for a return to normal).
     */
    data class RoomLine(val room: String, val temperature: String, val note: String, val detail: String) {
        val title: String get() = "$room — $temperature ($note)"
    }

    fun roomLine(item: AlertItem, now: LocalDateTime, withRule: Boolean): RoomLine {
        val v = item.values
        val e = item.event
        val since = e?.exceededSince?.let { at(it, now) } ?: v["since"].orEmpty()
        val note = when (item.kind) {
            DeliveryKind.RESOLVED -> "maximum ${v["peak"]}"
            else -> "seuil ${v["threshold"]}"
        }
        val parts = ArrayList<String>()
        v["place"]?.takeIf { it.isNotEmpty() }?.let { parts += it }
        parts += when (item.kind) {
            DeliveryKind.RESOLVED -> "dépassement de ${v["duration"]}, depuis $since"
            else -> "depuis $since (${v["duration"]})"
        }
        if (withRule) v["rule"]?.takeIf { it.isNotEmpty() }?.let { parts += "règle $it" }
        return RoomLine(v["room"].orEmpty(), v["temperature"].orEmpty(), note, parts.joinToString(" · "))
    }

    /** « 12:58 » the same day, « 07/11 à 12:58 » before. */
    fun at(date: LocalDateTime, now: LocalDateTime): String =
        if (date.toLocalDate() == now.toLocalDate()) TIME.format(date) else DAY.format(date) + " à " + TIME.format(date)

    fun sectionTitle(k: DeliveryKind) = section(k)

    private fun subject(one: AlertItem): String {
        val v = one.values
        val room = v.getValue("room")
        return when (one.kind) {
            DeliveryKind.TRIGGERED -> "[ThermoBox] Dépassement de température : $room (${v["temperature"]})"
            DeliveryKind.REMINDER -> "[ThermoBox] Rappel : dépassement toujours en cours, $room (${v["temperature"]})"
            DeliveryKind.RESOLVED -> "[ThermoBox] Retour à la normale : $room"
            DeliveryKind.TEST -> "[ThermoBox] Test du canal « ${v["channel"]} »"
        }
    }

    /** « 3 dépassements, 1 retour à la normale » */
    private fun summary(items: List<AlertItem>): String = ORDER.mapNotNull { k ->
        val n = items.count { it.kind == k }
        when {
            n == 0 -> null
            k == DeliveryKind.TRIGGERED -> if (n == 1) "1 dépassement" else "$n dépassements"
            k == DeliveryKind.REMINDER -> if (n == 1) "1 dépassement toujours en cours" else "$n dépassements toujours en cours"
            k == DeliveryKind.RESOLVED -> if (n == 1) "1 retour à la normale" else "$n retours à la normale"
            else -> if (n == 1) "1 test" else "$n tests"
        }
    }.joinToString(", ")

    private fun section(k: DeliveryKind) = when (k) {
        DeliveryKind.TRIGGERED -> "Nouveaux dépassements"
        DeliveryKind.REMINDER -> "Toujours au-dessus du seuil"
        DeliveryKind.RESOLVED -> "Revenues à la normale"
        DeliveryKind.TEST -> "Tests"
    }

    /** One room in a message about several: its own template, or its two lines (to size the parts). */
    private fun line(item: AlertItem, template: String?): String {
        template?.takeIf { it.isNotBlank() }?.let { return render(it, item.values) }
        return roomLine(item, LocalDateTime.now(), true).let { "\n• ${it.title}\n  ${it.detail}" }
    }

    /** Characters of the summary, the date and the section titles, kept apart when splitting. */
    private const val HEADER = 200
    private const val SECTION = 1

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
