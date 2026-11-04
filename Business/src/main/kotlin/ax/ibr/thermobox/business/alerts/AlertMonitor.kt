package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.business.climate.ClimateSnapshot
import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.persistence.dataservices.AlertDataService
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** What the monitor sees of the hierarchy: rooms, where they are, their boxes and setpoints. */
interface AlertView {
    fun rooms(): List<Room>
    /** Ids from the building down to the parent of [room]. */
    fun ancestors(room: Room): List<Long>
    /** « Bâtiment A › Étage 1 ». */
    fun place(room: Room): String
    fun devices(room: Room): List<Device>
    fun setpoint(room: Room): Float?
    /** Latest measurement of each box of [room] (the rooms of a rule, GET /rules/{id}/rooms). */
    fun latest(room: Room): List<Temperature> = emptyList()

    companion object {
        /** From a [ClimateSnapshot]: two queries for the whole hierarchy. */
        fun of(s: ClimateSnapshot): AlertView = object : AlertView {
            override fun rooms() = s.elements.values.filter { it.kind == RoomKind.ROOM }
            override fun ancestors(room: Room) = s.path(room).mapNotNull { it.id }
            override fun place(room: Room) = s.path(room).mapNotNull { it.name }.joinToString(" › ")
            override fun devices(room: Room) = s.devices(room)
            override fun setpoint(room: Room) = s.effectiveSetpoint(room)?.setpoint?.value
            override fun latest(room: Room) = s.measurements(room).mapNotNull { it.measurement }
        }
    }
}

/** State of the monitor, for the administrators. */
data class MonitorStatus(
    val enabled: Boolean,
    val running: Boolean,
    val lastRun: LocalDateTime?,
    /** Rooms checked, under at least one rule, at the last run. */
    val watchedRooms: Int,
    val lastError: String?,
    /** Another server sharing the database did the last run. */
    val runByAnotherServer: Boolean,
)

/**
 * Watches the rooms and sends the alerts, on the server, whoever is connected: every
 * [AlertConfig.interval], it compares the latest measures of the boxes to the rules
 * ([AlertEvaluator]), opens, updates and closes the events, and sends the messages due.
 *
 * - Nothing is kept in memory between two runs: the durations are read from the measures and the
 *   alerts in progress from the database, so a restart loses nothing (and does not alert again).
 * - Servers sharing the database: the run takes a MySQL lock, only one does it.
 * - A message is a [AlertDelivery] saved before it is sent: a failure is tried again later
 *   ([BACKOFF], at most [MAX_ATTEMPTS] times), then marked FAILED and written in the server's log.
 * - All of this runs in its own thread: a slow SMTP server or webhook never delays the reception
 *   of the measures.
 */
class AlertMonitor(
    private val store: AlertDataService,
    private val sender: AlertSender,
    private val config: AlertConfig,
    private val view: () -> AlertView,
    /** Measures of these boxes over the period (RoomTempAttrService.getMeasurementsOf). */
    private val measures: (List<Device>, LocalDateTime, LocalDateTime) -> Map<Long, List<Temperature>>,
    private val clock: () -> LocalDateTime = { LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) },
    private val log: (String) -> Unit = { println("[ThermoBox alerts] $it") },
) {
    private var scheduler: ScheduledExecutorService? = null
    @Volatile private var lastRun: LocalDateTime? = null
    @Volatile private var lastError: String? = null
    @Volatile private var watched = 0
    @Volatile private var elsewhere = false
    private var lastPurge: LocalDateTime? = null

    @Synchronized
    fun start() {
        if (!config.enabled || scheduler != null) return
        val s = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "thermobox-alerts").apply { isDaemon = true } }
        s.scheduleWithFixedDelay({ runCatching { run() } }, 20, config.interval.seconds, TimeUnit.SECONDS)
        scheduler = s
    }

    @Synchronized
    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    fun status() = MonitorStatus(config.enabled, scheduler != null, lastRun, watched, lastError, elsewhere)

    /** One run, unless another server is doing it. */
    fun run() {
        try {
            val done = store.exclusively(LOCK) {
                check()
                deliver()
                purge()
            }
            elsewhere = done == null
            lastError = null
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            log("run failed: $lastError")
        }
        lastRun = clock()
    }

    // ------------------------------------------------------------------ detection

    internal fun check() {
        val now = clock()
        val rules = store.rules().filter { it.enabled }
        val active = store.activeEvents()
        val byKey = active.associateBy { it.ruleId to it.roomId }
        val seen = HashSet<Pair<Long?, Long>>()
        if (rules.isNotEmpty()) {
            val v = view()
            val channels = store.channels().associateBy { it.id }
            val targets = v.rooms().mapNotNull { room ->
                val id = room.id ?: return@mapNotNull null
                val applicable = AlertEvaluator.applicable(rules, id, v.ancestors(room))
                if (applicable.isEmpty()) null else Triple(room, id, applicable)
            }
            watched = targets.size
            val devices = targets.associate { (room, id, _) -> id to v.devices(room) }
            val window = AlertEvaluator.window(rules)
            val series = devices.values.flatten().distinctBy { it.id }.takeIf { it.isNotEmpty() }
                ?.let { measures(it, now.minus(window), now) }.orEmpty()

            for ((room, id, applicable) in targets) {
                val setpoint = v.setpoint(room)
                val readings = devices[id].orEmpty().mapNotNull { d -> d.id?.let { DeviceReading(it, d.name, series[it].orEmpty()) } }
                for (rule in applicable) {
                    val key = rule.id to id
                    val existing = byKey[key]
                    if (setpoint == null) continue
                    seen += key
                    val statuses = readings.map { AlertEvaluator.status(it, setpoint + rule.tolerance, now) }
                    if (existing == null) open(rule, room, v.place(room), setpoint, statuses, channels, now)
                    else follow(existing, rule, setpoint, statuses, channels, now)
                }
            }
        } else {
            watched = 0
        }
        // Alerts whose rule, room or setpoint is gone
        for (e in active) {
            if ((e.ruleId to e.roomId) in seen) continue
            val rule = e.ruleId?.let { id -> rules.firstOrNull { it.id == id } }
            val reason = when {
                e.ruleId == null || store.rule(e.ruleId!!) == null -> "Règle supprimée"
                rule == null -> "Règle désactivée"
                else -> "Salle supprimée, sortie de la règle ou sans consigne"
            }
            store.save(e.copy(state = AlertState.CANCELLED, resolvedAt = now, reason = reason))
        }
    }

    private fun open(rule: AlertRule, room: Room, place: String, setpoint: Float, statuses: List<DeviceStatus>, channels: Map<Long?, AlertChannel>, now: LocalDateTime) {
        val since = AlertEvaluator.excessSince(statuses, Duration.ofSeconds(rule.minDurationSeconds.toLong()), now) ?: return
        val hottest = statuses.filter { it.fresh && it.exceeding }.maxOf { it.latest ?: Float.NEGATIVE_INFINITY }
        val event = store.save(AlertEvent(
            ruleId = rule.id, ruleName = rule.name, roomId = room.id!!, roomName = room.name, place = place,
            exceededSince = since, triggeredAt = now, setpoint = setpoint, threshold = setpoint + rule.tolerance,
            temperature = hottest, devices = statuses.map { it.toAlertDevice() }, lastNotifiedAt = now,
        ))
        log("excess in ${room.name} (${AlertMessages.celsius(hottest)}, threshold ${AlertMessages.celsius(event.threshold)}), rule ${rule.name}")
        queue(event, rule, DeliveryKind.TRIGGERED, channels, now)
    }

    private fun follow(e: AlertEvent, rule: AlertRule, setpoint: Float, statuses: List<DeviceStatus>, channels: Map<Long?, AlertChannel>, now: LocalDateTime) {
        val fresh = statuses.filter { it.fresh && it.latest != null }
        val latest = fresh.maxOfOrNull { it.latest!! } ?: e.lastTemperature
        var updated = e.copy(
            currentSetpoint = setpoint,
            lastTemperature = latest,
            peak = maxOf(e.peak, fresh.filter { it.exceeding }.maxOfOrNull { it.latest!! } ?: e.peak),
            devices = if (statuses.isEmpty()) e.devices else statuses.map { it.toAlertDevice() },
            ruleName = rule.name,
        )
        if (AlertEvaluator.backToNormal(statuses, Duration.ofSeconds(rule.clearAfterSeconds.toLong()), now)) {
            updated = store.save(updated.copy(state = AlertState.RESOLVED, resolvedAt = now))
            log("back to normal in ${e.roomName}, rule ${rule.name}")
            if (rule.notifyResolution) queue(updated, rule, DeliveryKind.RESOLVED, channels, now)
            return
        }
        val last = e.lastNotifiedAt ?: e.triggeredAt
        if (rule.reminderSeconds > 0 && Duration.between(last, now).seconds >= rule.reminderSeconds) {
            updated = store.save(updated.copy(lastNotifiedAt = now, reminders = e.reminders + 1))
            queue(updated, rule, DeliveryKind.REMINDER, channels, now)
            return
        }
        if (updated != e) store.save(updated)
    }

    private fun queue(e: AlertEvent, rule: AlertRule, kind: DeliveryKind, channels: Map<Long?, AlertChannel>, now: LocalDateTime) {
        for (id in rule.channels) {
            val c = channels[id] ?: continue
            if (!c.enabled) continue
            store.save(AlertDelivery(eventId = e.id, channelId = c.id, channelName = c.name, kind = kind, createdAt = now))
        }
    }

    // ------------------------------------------------------------------ sending

    internal fun deliver() {
        val now = clock()
        val due = store.dueDeliveries(now, BATCH)
        if (due.isEmpty()) return
        val channels = store.channels().associateBy { it.id }
        val events = HashMap<Long, AlertEvent?>()
        // A few at once: one slow server does not hold back the others
        val pool = Executors.newFixedThreadPool(minOf(PARALLEL, due.size)) { r -> Thread(r, "thermobox-alerts-send").apply { isDaemon = true } }
        try {
            val jobs = due.map { d ->
                val channel = d.channelId?.let { channels[it] }
                val event = d.eventId?.let { id -> events.getOrPut(id) { store.event(id) } }
                d to pool.submit<SendResult> {
                    when {
                        channel == null -> SendResult.Failed("Channel deleted")
                        !channel.enabled -> SendResult.Failed("Channel disabled")
                        d.eventId != null && event == null -> SendResult.Failed("Alert deleted")
                        else -> sender.send(channel, AlertMessages.of(d.kind, event, channel.name, now, null))
                    }
                }
            }
            for ((d, job) in jobs) {
                val result = try {
                    job.get(SEND_TIMEOUT.seconds, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    job.cancel(true)
                    SendResult.Retry("Sending took too long or failed: ${e.message ?: e.javaClass.simpleName}")
                }
                store.save(after(d, result, now))
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /** [d] once [result] is known: sent, to try again later, or given up (logged). */
    internal fun after(d: AlertDelivery, result: SendResult, now: LocalDateTime): AlertDelivery = when (result) {
        SendResult.Sent -> d.copy(status = DeliveryStatus.SENT, attempts = d.attempts + 1, sentAt = now, nextAttemptAt = null)
        is SendResult.Postponed -> d.copy(nextAttemptAt = now.plus(result.after))
        is SendResult.Failed -> {
            log("${d.kind} to ${d.channelName} failed: ${result.error}")
            d.copy(status = DeliveryStatus.FAILED, attempts = d.attempts + 1, lastError = result.error, nextAttemptAt = null)
        }
        is SendResult.Retry -> {
            val attempts = d.attempts + 1
            if (attempts >= MAX_ATTEMPTS) {
                log("${d.kind} to ${d.channelName} failed after $attempts attempts: ${result.error}")
                d.copy(status = DeliveryStatus.FAILED, attempts = attempts, lastError = result.error, nextAttemptAt = null)
            } else {
                val wait = maxOf(BACKOFF[attempts - 1], result.after ?: Duration.ZERO)
                d.copy(attempts = attempts, lastError = result.error, nextAttemptAt = now.plus(wait))
            }
        }
    }

    private fun purge() {
        val now = clock()
        if (lastPurge?.let { Duration.between(it, now) < Duration.ofDays(1) } == true) return
        lastPurge = now
        val n = store.purge(now.minus(config.history))
        if (n > 0) log("$n old alerts deleted")
    }

    companion object {
        private const val LOCK = "thermobox_alerts"
        private const val BATCH = 50
        private const val PARALLEL = 4
        private val SEND_TIMEOUT: Duration = Duration.ofSeconds(60)
        const val MAX_ATTEMPTS = 6
        /** Wait before the 2nd, 3rd… attempt. */
        val BACKOFF: List<Duration> = listOf(1, 5, 15, 60, 180).map { Duration.ofMinutes(it.toLong()) }

        /** Measures read through the data services, for the monitor. */
        fun measuresOf(service: ax.ibr.thermobox.common.services.RoomTempAttrService): (List<Device>, LocalDateTime, LocalDateTime) -> Map<Long, List<Temperature>> =
            { devices, start, end ->
                val zone = ZoneId.systemDefault()
                service.getMeasurementsOf(devices, start.atZone(zone).toInstant(), end.atZone(zone).toInstant())
            }
    }
}
