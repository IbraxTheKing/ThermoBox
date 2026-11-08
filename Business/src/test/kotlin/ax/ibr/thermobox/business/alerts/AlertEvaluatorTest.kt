package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.entities.Measurement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class AlertEvaluatorTest {

    private val now = LocalDateTime.of(2026, 11, 3, 12, 0)

    private fun reading(vararg values: Pair<Long, Float>) =
        DeviceReading(1, "Box", values.map { (minutesAgo, v) -> Measurement(v, now.minusMinutes(minutesAgo)) })

    @Test
    fun `the nearest rule wins, the ones at the same distance all apply`() {
        val global = AlertRule(id = 1, name = "all", global = true)
        val building = AlertRule(id = 2, name = "building", global = false, scope = listOf(10))
        val floor = AlertRule(id = 3, name = "floor", global = false, scope = listOf(20))
        val floor2 = AlertRule(id = 4, name = "floor too", global = false, scope = listOf(20, 99))
        val off = AlertRule(id = 5, name = "room, disabled", enabled = false, global = false, scope = listOf(30))
        val all = listOf(global, building, floor, floor2, off)
        // Room 30 on floor 20 of building 10
        assertEquals(listOf(3L, 4L), AlertEvaluator.applicable(all, 30, listOf(10, 20)).map { it.id })
        assertEquals(listOf(2L), AlertEvaluator.applicable(all - floor - floor2, 30, listOf(10, 20)).map { it.id })
        assertEquals(listOf(1L), AlertEvaluator.applicable(all, 31, listOf(11, 21)).map { it.id })
        assertTrue(AlertEvaluator.applicable(listOf(building, off), 31, listOf(11)).isEmpty())
        // A rule scoped to nothing (its elements were deleted) applies nowhere
        assertTrue(AlertEvaluator.applicable(listOf(AlertRule(name = "empty", global = false)), 30, listOf(10, 20)).isEmpty())
    }

    @Test
    fun `a box is above since its first measure of the current run`() {
        val s = AlertEvaluator.status(reading(30L to 22f, 20L to 24f, 10L to 22.5f, 5L to 24f, 0L to 25f), 23f, now)
        assertTrue(s.exceeding)
        assertEquals(now.minusMinutes(5), s.since)
        assertEquals(25f, s.latest)
        assertTrue(s.fresh)
        assertEquals(now.minusMinutes(5), AlertEvaluator.excessSince(listOf(s), Duration.ofMinutes(5), now))
        assertNull(AlertEvaluator.excessSince(listOf(s), Duration.ofMinutes(6), now))
    }

    @Test
    fun `back to normal when every box that measures is under for long enough`() {
        val under = AlertEvaluator.status(reading(25L to 25f, 15L to 22f, 0L to 21f), 23f, now)
        val stale = AlertEvaluator.status(reading(45L to 30f), 23f, now)
        assertFalse(stale.fresh)
        assertTrue(AlertEvaluator.backToNormal(listOf(under, stale), Duration.ofMinutes(15), now))
        assertFalse(AlertEvaluator.backToNormal(listOf(under), Duration.ofMinutes(16), now))
        // Nothing measures any more: nothing is decided
        assertFalse(AlertEvaluator.backToNormal(listOf(stale), Duration.ZERO, now))
        assertNull(AlertEvaluator.excessSince(listOf(stale), Duration.ZERO, now))
    }

    @Test
    fun `the window covers the longest duration, within 6 h`() {
        assertEquals(Duration.ofMinutes(40), AlertEvaluator.window(listOf(AlertRule(name = "a", minDurationSeconds = 600, clearAfterSeconds = 300))))
        assertEquals(AlertEvaluator.MAX_WINDOW, AlertEvaluator.window(listOf(AlertRule(name = "b", clearAfterSeconds = 6 * 3600))))
    }

    @Test
    fun `messages tell the room, the place, the temperatures and the boxes`() {
        val e = ax.ibr.thermobox.common.alerts.AlertEvent(
            id = 1, ruleId = 1, ruleName = "Serveurs", roomId = 3, roomName = "Salle serveurs", place = "Bâtiment A › Étage 1",
            exceededSince = now.minusMinutes(75), triggeredAt = now.minusMinutes(65), setpoint = 22f, threshold = 23f, temperature = 25.4f,
            devices = listOf(ax.ibr.thermobox.common.alerts.AlertDevice(4, "Baie 1", 25.4f, now, true)),
        )
        val m = AlertMessages.of(ax.ibr.thermobox.common.alerts.DeliveryKind.TRIGGERED, e, "Astreinte", now)
        assertEquals("[ThermoBox] Dépassement de température : Salle serveurs (25,4 °C)", m.subject)
        listOf("Lieu : Bâtiment A › Étage 1", "Consigne : 22,0 °C, seuil d'alerte : 23,0 °C", "(1 h 15)", "Boîtiers : Baie 1 25,4 °C", "Règle : Serveurs")
            .forEach { assertTrue(it in m.text, "$it\n${m.text}") }
        val custom = AlertMessages.of(ax.ibr.thermobox.common.alerts.DeliveryKind.TRIGGERED, e, "x", now, "{type} {room} : {temperature} > {threshold} {unknown}")
        assertEquals("Dépassement Salle serveurs : 25,4 °C > 23,0 °C {unknown}", custom.text)
    }

    @Test
    fun `alerts of many rooms are split when they do not fit, most urgent first`() {
        fun event(i: Int) = ax.ibr.thermobox.common.alerts.AlertEvent(
            id = i.toLong(), ruleId = 1, ruleName = "Tout le site", roomId = 100L + i, roomName = "Salle %02d".format(i), place = "Bâtiment A",
            exceededSince = now.minusMinutes(20), triggeredAt = now.minusMinutes(10), setpoint = 22f, threshold = 23f, temperature = 25f,
        )
        val kinds = listOf(ax.ibr.thermobox.common.alerts.DeliveryKind.RESOLVED, ax.ibr.thermobox.common.alerts.DeliveryKind.TRIGGERED)
        val entries = (1..60).map { AlertMessages.Entry(it, kinds[it % 2], event(it)) }
        val messages = AlertMessages.digest(entries, "Discord", now, MessageLimits.DISCORD)

        assertTrue(messages.size > 1, "${messages.size} message(s)")
        assertEquals((1..60).toSet(), messages.flatMap { it.second }.toSet())
        messages.forEachIndexed { i, (m, keys) ->
            assertTrue(m.text.length <= MessageLimits.DISCORD.chars, "${m.text.length}")
            assertTrue(keys.size <= MessageLimits.DISCORD.items)
            assertTrue(m.subject.endsWith("(${i + 1}/${messages.size})"), m.subject)
        }
        // The excesses before the returns to normal
        assertEquals(ax.ibr.thermobox.common.alerts.DeliveryKind.TRIGGERED, messages.first().first.kind)
        assertEquals(ax.ibr.thermobox.common.alerts.DeliveryKind.RESOLVED, messages.last().first.items.last().kind)

        // One room: the detailed message, as before
        val one = AlertMessages.digest(entries.take(1), "Discord", now, MessageLimits.DISCORD).single().first
        assertEquals("[ThermoBox] Dépassement de température : Salle 01 (25,0 °C)", one.subject)
    }
}
