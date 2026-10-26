package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.business.rules.SetpointRules
import ax.ibr.thermobox.business.settings.Range
import ax.ibr.thermobox.business.settings.TemperatureLimits
import ax.ibr.thermobox.business.settings.TemperatureLimitsStore
import ax.ibr.thermobox.business.rules.HierarchyRules
import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.services.DeviceHistory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime

class HierarchyClimateTest {

    private fun <T : Room> T.withId(id: Long) = apply { this.id = id }

    private val building = Building("B").withId(1)
    private val floor = Floor("E").withId(2)
    private val room = Room("S").withId(3)
    private val device = Device("X").withId(4)

    @Test
    fun `allowed links, including a room directly in a building`() {
        assertDoesNotThrow { HierarchyRules.checkLink(building, floor) }
        assertDoesNotThrow { HierarchyRules.checkLink(floor, room) }
        assertDoesNotThrow { HierarchyRules.checkLink(building, room) }
        assertDoesNotThrow { HierarchyRules.checkLink(room, device) }
    }

    @Test
    fun `links that skip or invert a level are refused`() {
        listOf(room to floor, floor to building, building to device, floor to device, room to room).forEach { (p, c) ->
            assertThrows(IllegalArgumentException::class.java) { HierarchyRules.checkLink(p, c) }
        }
    }

    @Test
    fun `the average keeps the last value of each device`() {
        val t0 = LocalDateTime.of(2026, 1, 1, 10, 0)
        val histories = listOf(
            DeviceHistory(Device("A").withId(10), listOf(Measurement(20f, t0), Measurement(22f, t0.plusMinutes(10)))),
            DeviceHistory(Device("B").withId(11), listOf(Measurement(18f, t0.plusMinutes(5)))),
        )

        val average = ClimateServiceImpl.movingAverage(histories)

        // 10:00: A alone; 10:05: (20 + 18) / 2; 10:10: (22 + 18) / 2
        assertEquals(listOf(20f, 19f, 20f), average.map { it.value })
        assertEquals(listOf(t0, t0.plusMinutes(5), t0.plusMinutes(10)), average.map { it.date })
    }

    @Test
    fun `an out-of-range setpoint is refused`() {
        SetpointRules.limits = { TemperatureLimits.DEFAULT }
        assertDoesNotThrow { SetpointRules.check(21f) }
        assertThrows(TooHotException::class.java) { SetpointRules.check(60f) }
        assertThrows(TooHotException::class.java) { SetpointRules.check(150f) }
        assertThrows(ImpossibleValueException::class.java) { SetpointRules.check(-20f) }
    }

    @Test
    fun `the limits chosen by an administrator apply`() {
        SetpointRules.limits = { TemperatureLimits(Range(0f, 90f), Range(15f, 30f)) }
        try {
            assertThrows(ImpossibleValueException::class.java) { SetpointRules.check(10f) }
            assertThrows(TooHotException::class.java) { SetpointRules.check(31f) }
            assertDoesNotThrow { SetpointRules.check(30f) }
            assertTrue(SetpointRules.acceptsMeasurement(85f))
            assertFalse(SetpointRules.acceptsMeasurement(-1f))
        } finally {
            SetpointRules.limits = { TemperatureLimits.DEFAULT }
        }
    }

    @Test
    fun `the limits are kept in the data folder`() {
        val store = TemperatureLimitsStore(java.nio.file.Files.createTempDirectory("limits"))
        assertEquals(TemperatureLimits.DEFAULT, store.get())
        val limits = TemperatureLimits(Range(-20f, 80f), Range(5f, 35f))
        store.set(limits)
        assertEquals(limits, store.get())
        assertThrows(IllegalArgumentException::class.java) { TemperatureLimits(Range(30f, 20f), Range(5f, 35f)) }
        assertThrows(IllegalArgumentException::class.java) { TemperatureLimits(Range(-500f, 20f), Range(5f, 35f)) }
        store.reset()
        assertEquals(TemperatureLimits.DEFAULT, store.get())
    }
}
