package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertDevice
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.persistence.dataservices.AlertDataService
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** [AlertDataService] in plain SQL (tables of migration V9). */
class AlertDataServiceJdbcImpl(
    private val db: JdbcDatabase,
    private val clock: () -> LocalDateTime = { LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) },
) : AlertDataService {

    // ------------------------------------------------------------------ channels

    override fun channels(): List<AlertChannel> = db.read { cx -> cx.query("SELECT * FROM alert_channel ORDER BY name, id") { channelOf(it) } }

    override fun channel(id: Long): AlertChannel? =
        db.read { cx -> cx.query("SELECT * FROM alert_channel WHERE id = ?", id) { channelOf(it) }.firstOrNull() }

    override fun save(channel: AlertChannel): AlertChannel = db.transaction { cx ->
        val now = clock()
        val id = channel.id
        if (id == null) {
            val created = cx.insert(
                "INSERT INTO alert_channel (name, kind, enabled, settings, secret, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                channel.name, channel.kind.name, channel.enabled, channel.settings, channel.secret, now, now,
            )
            channel.copy(id = created, createdAt = now, updatedAt = now)
        } else {
            val n = cx.execute(
                "UPDATE alert_channel SET name = ?, kind = ?, enabled = ?, settings = ?, secret = ?, updated_at = ? WHERE id = ?",
                channel.name, channel.kind.name, channel.enabled, channel.settings, channel.secret, now, id,
            )
            require(n == 1) { "Unknown channel $id" }
            channel.copy(updatedAt = now)
        }
    }

    override fun deleteChannel(id: Long): Boolean = db.transaction { cx -> cx.execute("DELETE FROM alert_channel WHERE id = ?", id) == 1 }

    private fun channelOf(rs: ResultSet) = AlertChannel(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        kind = ChannelKind.valueOf(rs.getString("kind")),
        enabled = rs.getBoolean("enabled"),
        settings = rs.getString("settings"),
        secret = rs.getString("secret"),
        createdAt = rs.dateTime("created_at"),
        updatedAt = rs.dateTime("updated_at"),
    )

    // ------------------------------------------------------------------ rules

    override fun rules(): List<AlertRule> = db.read { cx -> withLinks(cx, cx.query("SELECT * FROM alert_rule ORDER BY name, id") { ruleOf(it) }) }

    override fun rule(id: Long): AlertRule? =
        db.read { cx -> withLinks(cx, cx.query("SELECT * FROM alert_rule WHERE id = ?", id) { ruleOf(it) }).firstOrNull() }

    override fun save(rule: AlertRule): AlertRule = db.transaction { cx ->
        val now = clock()
        val saved = if (rule.id == null) {
            val id = cx.insert(
                "INSERT INTO alert_rule (name, enabled, tolerance, min_duration_seconds, clear_after_seconds, reminder_seconds, " +
                    "notify_resolution, global_scope, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rule.name, rule.enabled, rule.tolerance, rule.minDurationSeconds, rule.clearAfterSeconds, rule.reminderSeconds,
                rule.notifyResolution, rule.global, now, now,
            )
            rule.copy(id = id, createdAt = now, updatedAt = now)
        } else {
            val n = cx.execute(
                "UPDATE alert_rule SET name = ?, enabled = ?, tolerance = ?, min_duration_seconds = ?, clear_after_seconds = ?, " +
                    "reminder_seconds = ?, notify_resolution = ?, global_scope = ?, updated_at = ? WHERE id = ?",
                rule.name, rule.enabled, rule.tolerance, rule.minDurationSeconds, rule.clearAfterSeconds, rule.reminderSeconds,
                rule.notifyResolution, rule.global, now, rule.id,
            )
            require(n == 1) { "Unknown rule ${rule.id}" }
            cx.execute("DELETE FROM alert_rule_scope WHERE rule_id = ?", rule.id)
            cx.execute("DELETE FROM alert_rule_channel WHERE rule_id = ?", rule.id)
            rule.copy(updatedAt = now)
        }
        val scope = if (saved.global) emptyList() else saved.scope.distinct()
        for (e in scope) cx.execute("INSERT INTO alert_rule_scope (rule_id, element_id) VALUES (?, ?)", saved.id, e)
        for (c in saved.channels.distinct()) cx.execute("INSERT INTO alert_rule_channel (rule_id, channel_id) VALUES (?, ?)", saved.id, c)
        saved.copy(scope = scope, channels = saved.channels.distinct())
    }

    override fun deleteRule(id: Long): Boolean = db.transaction { cx -> cx.execute("DELETE FROM alert_rule WHERE id = ?", id) == 1 }

    private fun ruleOf(rs: ResultSet) = AlertRule(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        enabled = rs.getBoolean("enabled"),
        tolerance = rs.getFloat("tolerance"),
        minDurationSeconds = rs.getInt("min_duration_seconds"),
        clearAfterSeconds = rs.getInt("clear_after_seconds"),
        reminderSeconds = rs.getInt("reminder_seconds"),
        notifyResolution = rs.getBoolean("notify_resolution"),
        global = rs.getBoolean("global_scope"),
        createdAt = rs.dateTime("created_at"),
        updatedAt = rs.dateTime("updated_at"),
    )

    /** The scope and the channels of [rules], in two queries. */
    private fun withLinks(cx: Connection, rules: List<AlertRule>): List<AlertRule> {
        if (rules.isEmpty()) return rules
        val ids = rules.mapNotNull { it.id }
        val ph = placeholders(ids.size)
        val scope = cx.query("SELECT rule_id, element_id FROM alert_rule_scope WHERE rule_id IN ($ph) ORDER BY element_id", *ids.toTypedArray()) {
            it.getLong("rule_id") to it.getLong("element_id")
        }.groupBy({ it.first }, { it.second })
        val channels = cx.query("SELECT rule_id, channel_id FROM alert_rule_channel WHERE rule_id IN ($ph) ORDER BY channel_id", *ids.toTypedArray()) {
            it.getLong("rule_id") to it.getLong("channel_id")
        }.groupBy({ it.first }, { it.second })
        return rules.map { it.copy(scope = scope[it.id].orEmpty(), channels = channels[it.id].orEmpty()) }
    }

    // ------------------------------------------------------------------ events

    override fun events(state: AlertState?, roomId: Long?, ruleId: Long?, before: Long?, limit: Int): List<AlertEvent> = db.read { cx ->
        val where = ArrayList<String>()
        val params = ArrayList<Any?>()
        state?.let { where += "e.state = ?"; params += it.name }
        roomId?.let { where += "e.room_id = ?"; params += it }
        ruleId?.let { where += "e.rule_id = ?"; params += it }
        before?.let { where += "e.id < ?"; params += it }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        params += limit.coerceIn(1, 1000)
        cx.query("SELECT e.*, $FAILED AS failed FROM alert_event e $clause ORDER BY e.id DESC LIMIT ?", *params.toTypedArray()) { eventOf(it) }
    }

    override fun event(id: Long): AlertEvent? =
        db.read { cx -> cx.query("SELECT e.*, $FAILED AS failed FROM alert_event e WHERE e.id = ?", id) { eventOf(it) }.firstOrNull() }

    override fun activeEvents(): List<AlertEvent> = events(AlertState.ACTIVE, limit = 1000)

    override fun save(event: AlertEvent): AlertEvent = db.transaction { cx ->
        val values = arrayOf<Any?>(
            event.ruleId, event.ruleName, event.roomId, event.roomName, event.place.take(500), event.state.name, event.exceededSince,
            event.triggeredAt, event.resolvedAt, event.setpoint, event.threshold, event.currentSetpoint, event.temperature, event.peak,
            event.lastTemperature, devicesText(event.devices), event.lastNotifiedAt, event.reminders, event.reason?.take(255),
        )
        if (event.id == null) {
            val id = cx.insert(
                "INSERT INTO alert_event (rule_id, rule_name, room_id, room_name, place, state, exceeded_since, triggered_at, resolved_at, " +
                    "setpoint, threshold, current_setpoint, temperature, peak, last_temperature, devices, last_notified_at, reminders, reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                *values,
            )
            event.copy(id = id)
        } else {
            cx.execute(
                "UPDATE alert_event SET rule_id = ?, rule_name = ?, room_id = ?, room_name = ?, place = ?, state = ?, exceeded_since = ?, " +
                    "triggered_at = ?, resolved_at = ?, setpoint = ?, threshold = ?, current_setpoint = ?, temperature = ?, peak = ?, " +
                    "last_temperature = ?, devices = ?, last_notified_at = ?, reminders = ?, reason = ? WHERE id = ?",
                *values, event.id,
            )
            event
        }
    }

    private fun eventOf(rs: ResultSet) = AlertEvent(
        id = rs.getLong("id"),
        ruleId = rs.longOrNull("rule_id"),
        ruleName = rs.getString("rule_name"),
        roomId = rs.getLong("room_id"),
        roomName = rs.getString("room_name"),
        place = rs.getString("place"),
        state = AlertState.valueOf(rs.getString("state")),
        exceededSince = rs.dateTime("exceeded_since")!!,
        triggeredAt = rs.dateTime("triggered_at")!!,
        resolvedAt = rs.dateTime("resolved_at"),
        setpoint = rs.getFloat("setpoint"),
        threshold = rs.getFloat("threshold"),
        currentSetpoint = rs.floatOrNull("current_setpoint"),
        temperature = rs.getFloat("temperature"),
        peak = rs.getFloat("peak"),
        lastTemperature = rs.floatOrNull("last_temperature"),
        devices = devicesOf(rs.getString("devices")),
        lastNotifiedAt = rs.dateTime("last_notified_at"),
        reminders = rs.getInt("reminders"),
        reason = rs.getString("reason"),
        deliveryFailed = rs.getBoolean("failed"),
    )

    // ------------------------------------------------------------------ deliveries

    override fun deliveries(eventId: Long): List<AlertDelivery> =
        db.read { cx -> cx.query("SELECT * FROM alert_delivery WHERE event_id = ? ORDER BY id", eventId) { deliveryOf(it) } }

    override fun deliveries(status: DeliveryStatus?, limit: Int): List<AlertDelivery> = db.read { cx ->
        if (status == null) cx.query("SELECT * FROM alert_delivery ORDER BY id DESC LIMIT ?", limit.coerceIn(1, 1000)) { deliveryOf(it) }
        else cx.query("SELECT * FROM alert_delivery WHERE status = ? ORDER BY id DESC LIMIT ?", status.name, limit.coerceIn(1, 1000)) { deliveryOf(it) }
    }

    override fun dueDeliveries(now: LocalDateTime, limit: Int): List<AlertDelivery> = db.read { cx ->
        cx.query(
            "SELECT * FROM alert_delivery WHERE status = ? AND (next_attempt_at IS NULL OR next_attempt_at <= ?) ORDER BY id LIMIT ?",
            DeliveryStatus.PENDING.name, now, limit,
        ) { deliveryOf(it) }
    }

    override fun save(delivery: AlertDelivery): AlertDelivery = db.transaction { cx ->
        val values = arrayOf<Any?>(
            delivery.eventId, delivery.channelId, delivery.channelName.take(100), delivery.kind.name, delivery.status.name,
            delivery.attempts, delivery.nextAttemptAt, delivery.lastError?.take(500), delivery.sentAt,
        )
        if (delivery.id == null) {
            val now = delivery.createdAt ?: clock()
            val id = cx.insert(
                "INSERT INTO alert_delivery (event_id, channel_id, channel_name, kind, status, attempts, next_attempt_at, last_error, sent_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                *values, now,
            )
            delivery.copy(id = id, createdAt = now)
        } else {
            cx.execute(
                "UPDATE alert_delivery SET event_id = ?, channel_id = ?, channel_name = ?, kind = ?, status = ?, attempts = ?, " +
                    "next_attempt_at = ?, last_error = ?, sent_at = ? WHERE id = ?",
                *values, delivery.id,
            )
            delivery
        }
    }

    private fun deliveryOf(rs: ResultSet) = AlertDelivery(
        id = rs.getLong("id"),
        eventId = rs.longOrNull("event_id"),
        channelId = rs.longOrNull("channel_id"),
        channelName = rs.getString("channel_name"),
        kind = DeliveryKind.valueOf(rs.getString("kind")),
        status = DeliveryStatus.valueOf(rs.getString("status")),
        attempts = rs.getInt("attempts"),
        nextAttemptAt = rs.dateTime("next_attempt_at"),
        lastError = rs.getString("last_error"),
        createdAt = rs.dateTime("created_at"),
        sentAt = rs.dateTime("sent_at"),
    )

    override fun purge(before: LocalDateTime): Int = db.transaction { cx ->
        // Deliveries of these events go with them (ON DELETE CASCADE)
        cx.execute("DELETE FROM alert_event WHERE state <> ? AND resolved_at < ?", AlertState.ACTIVE.name, before) +
            cx.execute("DELETE FROM alert_delivery WHERE event_id IS NULL AND created_at < ?", before)
    }

    // The lock is held by a connection outside the pool: the operations of [block] borrow theirs
    override fun <T> exclusively(name: String, block: () -> T): T? = db.alone { cx ->
        val got = cx.query("SELECT GET_LOCK(?, 0) AS got", name) { it.intOrNull("got") }.firstOrNull() == 1
        if (!got) return@alone null
        try {
            block()
        } finally {
            runCatching { cx.query("SELECT RELEASE_LOCK(?) AS r", name) { it.intOrNull("r") } }
        }
    }

    companion object {
        private const val FAILED = "EXISTS (SELECT 1 FROM alert_delivery d WHERE d.event_id = e.id AND d.status = 'FAILED')"

        // One box per line: id, exceeding (1/0), temperature, date, name; the name last, escaped
        internal fun devicesText(devices: List<AlertDevice>): String = devices.joinToString("\n") { d ->
            listOf(
                d.id.toString(), if (d.exceeding) "1" else "0", d.temperature?.toString().orEmpty(), d.at?.toString().orEmpty(),
                d.name.orEmpty().replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"),
            ).joinToString("\t")
        }

        internal fun devicesOf(text: String?): List<AlertDevice> =
            text.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val f = line.split("\t", limit = 5)
                if (f.size < 5) return@mapNotNull null
                AlertDevice(
                    id = f[0].toLongOrNull() ?: return@mapNotNull null,
                    exceeding = f[1] == "1",
                    temperature = f[2].toFloatOrNull(),
                    at = f[3].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                    name = unescape(f[4]).ifEmpty { null },
                )
            }

        private fun unescape(s: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                    i += 2
                } else {
                    out.append(c); i++
                }
            }
            return out.toString()
        }
    }
}
