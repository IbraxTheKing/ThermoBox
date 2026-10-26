package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

class InheritedSetpointTest {

    private fun <T : Room> T.withId(id: Long) = apply { this.id = id }

    // Building B → Floor E → rooms S1 (device X1) and S2 (device X2); room S3 (device X3) directly in B.
    private val b = Building("B").withId(1)
    private val e = Floor("E").withId(2)
    private val s1 = Room("S1").withId(3)
    private val s2 = Room("S2").withId(4)
    private val s3 = Room("S3").withId(5)
    private val x1 = Device("X1").withId(6)
    private val x2 = Device("X2").withId(7)
    private val x3 = Device("X3").withId(8)
    private val parents = mapOf(e to b, s1 to e, s2 to e, s3 to b, x1 to s1, x2 to s2, x3 to s3)

    private inner class FakeHierarchy : HierarchyService {
        override fun getRoots() = listOf(b)
        override fun getChildren(parent: Room) = parents.filterValues { it.id == parent.id }.keys.sortedBy { it.id }
        override fun getParent(child: Room) = parents.entries.firstOrNull { it.key.id == child.id }?.value
        override fun getPath(room: Room): List<Room> =
            generateSequence(getParent(room)) { getParent(it) }.toList().asReversed()
        override fun getDevices(room: Room) = emptyList<Device>()
        override fun getRooms(room: Room) = emptyList<Room>()
        override fun setParent(child: Room, parent: Room?) = Unit
    }

    /** Only what the setpoints need; the rest is never called here. */
    private class FakeTemperatures : RoomTempAttrService {
        val lines = mutableListOf<RoomTempAttr>()
        override fun add(t: RoomTempAttr) { lines += t }
        override fun getSetpointFromRoom(room: Room) = lines
            .filter { it.room.id == room.id && it.temperature is Setpoint }
            .maxByOrNull { it.temperature.date!! }?.temperature as Setpoint?
        override fun getSetpointsFromRoomTimed(room: Room, start: Instant, end: Instant) =
            lines.filter { it.room.id == room.id }.mapNotNull { it.temperature as? Setpoint }
        override fun update(t: RoomTempAttr) = TODO()
        override fun remove(t: RoomTempAttr) = TODO()
        override fun getAll() = lines.toList()
        override fun getById(id: Long) = TODO()
        override fun getByRoom(roomId: Long) = TODO()
        override fun getByRoom(room: Room) = TODO()
        override fun getByTemperature(temperature: Temperature) = TODO()
        override fun getByTemperature(temperatureId: Long) = TODO()
        override fun getCurrentTemperatureFromRoom(room: Room, temperatureClass: Temperature) = TODO()
        override fun getCurrentTemperaturesFromRoom(room: Room) = TODO()
        override fun getTemperaturesFromRoomTimed(room: Room, start: Instant, end: Instant) = TODO()
        override fun getAverageTemperatureFromRoom(room: Room, temperatureClass: Temperature) = TODO()
        override fun getAverageTemperatureFromRoomTimed(room: Room, start: Instant, end: Instant) = TODO()
        override fun getAllByTime(start: Instant, end: Instant) = TODO()
    }

    private val temperatures = FakeTemperatures()
    // No real box in tests: every send fails, which is enough to see which boxes were targeted.
    private val climate = ClimateServiceImpl(FakeHierarchy(), temperatures) { error("no device") }

    private fun setpointOf(room: Room, value: Float, minutes: Long = 0) =
        temperatures.add(RoomTempAttr(room, Setpoint(value, LocalDateTime.of(2026, 1, 1, 8, 0).plusMinutes(minutes))))

    @Test
    fun `a room without a setpoint follows its floor's, otherwise its building's`() {
        assertNull(climate.getSetpoint(s1))
        setpointOf(b, 19f)
        assertEquals(19f, climate.getSetpoint(s1)?.value)
        setpointOf(e, 20f)
        val effective = climate.getEffectiveSetpoint(s1)!!
        assertEquals(20f, effective.setpoint.value)
        assertEquals(e, effective.source)
        assertTrue(effective.inherited)
        assertEquals(19f, climate.getSetpoint(s3)?.value)
    }

    @Test
    fun `without forcing, rooms with their own setpoint keep it with their devices`() {
        setpointOf(s2, 23f)

        val dispatch = climate.sendSetpoint(b, 21f)

        assertEquals(listOf(s2), dispatch.kept.map { it.source })
        assertEquals(listOf(x1, x3), dispatch.devices)
        assertEquals(21f, climate.getSetpoint(s1)?.value)
        assertEquals(23f, climate.getSetpoint(s2)?.value)
        assertEquals(dispatch.devices, dispatch.failures)
    }

    @Test
    fun `when forced, the setpoint replaces the rooms' own and goes to every device`() {
        setpointOf(s2, 23f)
        setpointOf(e, 18f)

        val dispatch = climate.sendSetpoint(b, 21f, force = true)

        assertTrue(dispatch.kept.isEmpty())
        assertEquals(listOf(e, s2), dispatch.replaced)
        assertEquals(listOf(x1, x2, x3), dispatch.devices)
        listOf(e, s1, s2, s3).forEach { assertEquals(21f, climate.getSetpoint(it)?.value) }
    }

    @Test
    fun `after a forced setpoint, the rooms follow the building again`() {
        setpointOf(s2, 23f)
        climate.sendSetpoint(b, 21f, force = true)

        assertTrue(climate.getOwnSetpoints(b).isEmpty())
        assertTrue(climate.getEffectiveSetpoint(s2)!!.inherited)

        // A later setpoint, not forced, reaches them all.
        val dispatch = climate.sendSetpoint(b, 19f)
        assertTrue(dispatch.kept.isEmpty())
        assertEquals(listOf(x1, x2, x3), dispatch.devices)
        listOf(e, s1, s2, s3).forEach { assertEquals(19f, climate.getSetpoint(it)?.value) }
    }

    @Test
    fun `only rooms and floors count as own setpoints`() {
        setpointOf(x1, 25f) // last setpoint a device received
        setpointOf(s2, 23f)
        assertEquals(listOf(s2), climate.getOwnSetpoints(b).map { it.source })
        assertFalse(climate.getOwnSetpoints(s1).any())
    }
}
