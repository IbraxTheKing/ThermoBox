package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Temperature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class AlertMonitorTest {

    private val start = LocalDateTime.of(2026, 11, 3, 8, 0)
    private var now = start

    private val room = Room("Salle serveurs").apply { id = 3 }
    private val other = Room("Bureau").apply { id = 6 }
    private val d1 = Device("Baie 1").apply { id = 4 }
    private val d2 = Device("Baie 2").apply { id = 5 }
    private val d3 = Device("Bureau 1").apply { id = 7 }
    private var setpoint: Float? = 22f

    /** Measures of each box: (date, °C). */
    private val measures = HashMap<Long, MutableList<Temperature>>()

    private fun measure(device: Device, value: Float, at: LocalDateTime = now) {
        measures.getOrPut(device.id!!) { ArrayList() } += Measurement(value, at)
    }

    private val view = object : AlertView {
        override fun rooms() = listOf(room, other)
        override fun ancestors(room: Room) = listOf(1L, 2L)
        override fun place(room: Room) = "Bâtiment A › Étage 1"
        override fun devices(room: Room) = if (room === this@AlertMonitorTest.room) listOf(d1, d2) else listOf(d3)
        override fun setpoint(room: Room) = setpoint
    }

    private val store = FakeAlertStore()
    // Sent in parallel by the monitor
    private val sent = java.util.Collections.synchronizedList(ArrayList<Pair<String, AlertMessage>>())
    private var answer: (AlertChannel) -> SendResult = { SendResult.Sent }
    private val logs = ArrayList<String>()

    private val monitor = AlertMonitor(
        store = store,
        sender = { c, m -> answer(c).also { if (it == SendResult.Sent) sent += c.name to m } },
        config = AlertConfig(),
        view = { view },
        measures = { devices, from, to -> devices.associate { d -> d.id!! to measures[d.id].orEmpty().filter { !it.date!!.isBefore(from) && !it.date!!.isAfter(to) } } },
        clock = { now },
        log = { logs += it },
    )

    private val mail = store.save(AlertChannel(name = "Astreinte", kind = ChannelKind.EMAIL))
    private val hook = store.save(AlertChannel(name = "Discord", kind = ChannelKind.WEBHOOK))

    private fun rule(vararg changes: (AlertRule) -> AlertRule): AlertRule {
        var r = AlertRule(name = "Serveurs", tolerance = 1f, minDurationSeconds = 600, clearAfterSeconds = 600, global = false,
            scope = listOf(room.id!!), channels = listOf(mail.id!!, hook.id!!))
        changes.forEach { r = it(r) }
        return store.save(r)
    }

    /** Every 5 minutes for [minutes], each box measuring [value]: (d1, d2). */
    private fun run(minutes: Long, v1: Float, v2: Float = 21f) {
        val end = now.plusMinutes(minutes)
        while (now.isBefore(end)) {
            measure(d1, v1); measure(d2, v2); measure(d3, 30f)
            monitor.run()
            now = now.plusMinutes(5)
        }
    }

    @Test
    fun `an excess is alerted once it lasted, once, then resolved`() {
        rule()
        run(10, 22.8f)                       // under 23 °C (22 + 1): nothing
        assertTrue(store.events.isEmpty())
        run(5, 24f)                          // above since 08:10, not 10 min yet
        assertTrue(store.events.isEmpty())
        run(10, 24.5f)                       // 08:20: lasted 10 min
        val e = store.activeEvents().single()
        assertEquals(LocalDateTime.of(2026, 11, 3, 8, 10), e.exceededSince)
        assertEquals(LocalDateTime.of(2026, 11, 3, 8, 20), e.triggeredAt)
        assertEquals(24.5f, e.temperature)
        assertEquals(23f, e.threshold)
        assertEquals(listOf(true, false), e.devices.map { it.exceeding })
        // One message per channel, not one per measure
        assertEquals(listOf("Astreinte", "Discord"), sent.map { it.first }.sorted())
        assertTrue(sent.all { it.second.kind == DeliveryKind.TRIGGERED })
        assertTrue(sent[0].second.text.contains("Baie 1 24,5 °C"), sent[0].second.text)
        run(30, 26f)
        assertEquals(2, sent.size)
        assertEquals(26f, store.activeEvents().single().peak)

        run(5, 22f)                          // back under, not long enough
        assertEquals(AlertState.ACTIVE, store.activeEvents().single().state)
        run(10, 22f)
        val resolved = store.events.values.single()
        assertEquals(AlertState.RESOLVED, resolved.state)
        assertEquals(listOf(DeliveryKind.RESOLVED, DeliveryKind.RESOLVED), sent.drop(2).map { it.second.kind })
        assertTrue(sent.last().second.text.contains("maximum atteint : 26,0 °C"), sent.last().second.text)
    }

    @Test
    fun `the rooms in excess at once go in one message per channel`() {
        rule({ it.copy(global = true, scope = emptyList(), minDurationSeconds = 0) })
        run(5, 25f)                          // the servers' room and the office (30 °C) at once
        assertEquals(2, store.activeEvents().size)
        assertEquals(listOf("Astreinte", "Discord"), sent.map { it.first }.sorted())
        sent.forEach { (_, m) ->
            assertEquals(listOf("Bureau", "Salle serveurs"), m.items.mapNotNull { it.event?.roomName }.sorted())
            assertEquals("[ThermoBox] 2 dépassements", m.subject)
            assertTrue(m.text.contains("• Salle serveurs — ") && m.text.contains("• Bureau — "), m.text)
        }
        assertTrue(store.deliveries.values.all { it.status == DeliveryStatus.SENT })
    }

    @Test
    fun `reminders while it lasts, and no resolution message when not wanted`() {
        rule({ it.copy(minDurationSeconds = 0, reminderSeconds = 1800, notifyResolution = false, channels = listOf(mail.id!!)) })
        run(65, 25f)
        assertEquals(listOf(DeliveryKind.TRIGGERED, DeliveryKind.REMINDER, DeliveryKind.REMINDER), sent.map { it.second.kind })
        assertEquals(2, store.activeEvents().single().reminders)
        run(15, 21f)
        assertEquals(AlertState.RESOLVED, store.events.values.single().state)
        assertEquals(3, sent.size)
    }

    @Test
    fun `the nearest rule applies, a disabled one cancels its alert`() {
        val global = rule({ it.copy(name = "Partout", global = true, scope = emptyList(), tolerance = 5f, minDurationSeconds = 0) })
        val near = rule({ it.copy(name = "Serveurs", tolerance = 1f, minDurationSeconds = 0) })
        run(5, 25f)
        // The office (30 °C) only has the global rule; the server room only its own
        assertEquals(setOf(near.id to room.id, global.id to other.id), store.activeEvents().map { it.ruleId to it.roomId }.toSet())

        store.save(near.copy(enabled = false))
        run(5, 25f)
        val cancelled = store.events.values.single { it.ruleId == near.id && it.state == AlertState.CANCELLED }
        assertEquals("Règle désactivée", cancelled.reason)
        // Now under the global rule: 25 °C is not above 22 + 5
        assertTrue(store.activeEvents().none { it.roomId == room.id })
    }

    @Test
    fun `a new setpoint applies during an excess`() {
        rule({ it.copy(minDurationSeconds = 0, clearAfterSeconds = 0) })
        run(5, 25f)
        assertEquals(AlertState.ACTIVE, store.activeEvents().single().state)
        setpoint = 26f
        run(5, 25f)
        val e = store.events.values.single()
        assertEquals(AlertState.RESOLVED, e.state)
        assertEquals(26f, e.currentSetpoint)
        assertEquals(22f, e.setpoint)
    }

    @Test
    fun `a box that stopped measuring neither triggers nor resolves`() {
        rule({ it.copy(minDurationSeconds = 0, clearAfterSeconds = 0) })
        measure(d1, 30f, now.minusMinutes(40))
        monitor.run()
        assertTrue(store.events.isEmpty())
        run(5, 30f)
        // Then silence: the alert stays as it is
        now = now.plusHours(2)
        monitor.run()
        assertEquals(AlertState.ACTIVE, store.activeEvents().single().state)
    }

    @Test
    fun `failed messages are tried again, then given up and logged`() {
        rule({ it.copy(minDurationSeconds = 0, channels = listOf(hook.id!!)) })
        answer = { SendResult.Retry("503") }
        run(5, 25f)
        val d = store.deliveries.values.single()
        assertEquals(1, d.attempts)
        assertEquals(DeliveryStatus.PENDING, d.status)
        assertEquals(start.plusMinutes(1), d.nextAttemptAt)
        // Until the 6th attempt: 1, 5, 15, 60, 180 min apart
        repeat(5) {
            now = store.deliveries.values.single().nextAttemptAt!!
            monitor.run()
        }
        val failed = store.deliveries.values.single()
        assertEquals(DeliveryStatus.FAILED, failed.status)
        assertEquals(AlertMonitor.MAX_ATTEMPTS, failed.attempts)
        assertTrue(store.activeEvents().single().deliveryFailed)
        assertTrue(logs.any { it.contains("failed after 6 attempts: 503") }, logs.toString())
    }

    @Test
    fun `refused for good at once, postponed without counting`() {
        val m = AlertMonitor(store, { _, _ -> SendResult.Sent }, AlertConfig(), { view }, { _, _, _ -> emptyMap() }, { now }, { logs += it })
        val d = AlertDelivery(eventId = 1, channelId = 1, channelName = "x", kind = DeliveryKind.TRIGGERED)
        assertEquals(DeliveryStatus.FAILED, m.after(d, SendResult.Failed("404"), now).status)
        val p = m.after(d, SendResult.Postponed(Duration.ofSeconds(30)), now)
        assertEquals(0, p.attempts)
        assertEquals(now.plusSeconds(30), p.nextAttemptAt)
        assertEquals(now.plusMinutes(10), m.after(d, SendResult.Retry("429", Duration.ofMinutes(10)), now).nextAttemptAt)
    }

    @Test
    fun `another server holds the lock, nothing is done twice`() {
        rule({ it.copy(minDurationSeconds = 0) })
        store.lockedElsewhere = true
        run(5, 25f)
        assertTrue(store.events.isEmpty())
        assertTrue(monitor.status().runByAnotherServer)
        assertNull(monitor.status().lastError)
    }
}
