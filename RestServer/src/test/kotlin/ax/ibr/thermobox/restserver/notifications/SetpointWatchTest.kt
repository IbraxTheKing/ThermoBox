package ax.ibr.thermobox.restserver.notifications

import ax.ibr.thermobox.common.entities.Temperature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SetpointWatchTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 12, 1, 8, 0)

    /** One measure every 5 minutes from t0. */
    private fun curve(vararg values: Float) = values.mapIndexed { i, v -> Temperature(v, t0.plusMinutes(5L * i)) }

    @Test
    fun `the state of a room against its setpoint`() {
        assertEquals(SetpointState.STABLE, SetpointWatch.stateOf(20.3f, 20.5f))
        assertEquals(SetpointState.HEATING, SetpointWatch.stateOf(20.0f, 20.5f))
        assertEquals(SetpointState.TOO_HOT, SetpointWatch.stateOf(21.0f, 20.5f))
    }

    @Test
    fun `a room that reaches its setpoint`() {
        val c = curve(18f, 18.5f, 19f, 19.6f, 20.2f, 20.4f, 20.5f, 20.5f)
        val changes = SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(40))
        assertEquals(1, changes.size)
        val change = changes[0]
        assertEquals(SetpointState.HEATING, change.previous)
        assertEquals(SetpointState.STABLE, change.state)
        // Dated from its first measure within half a degree
        assertEquals(t0.plusMinutes(20), change.at)
    }

    @Test
    fun `a measure that crosses the line and comes back is not a change`() {
        val c = curve(20.5f, 20.5f, 21.2f, 20.5f, 20.4f, 20.6f, 20.5f)
        assertTrue(SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(30)).isEmpty())
    }

    @Test
    fun `a room that goes too hot, then comes back`() {
        val c = curve(20.5f, 20.5f, 20.6f, 21.2f, 21.4f, 21.5f, 21.3f, 20.8f, 20.6f, 20.5f, 20.4f, 20.5f)
        val changes = SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(55))
        assertEquals(listOf(SetpointState.TOO_HOT, SetpointState.STABLE), changes.map { it.state })
        assertEquals(listOf(SetpointState.STABLE, SetpointState.TOO_HOT), changes.map { it.previous })
    }

    @Test
    fun `only the changes since the date asked for`() {
        val c = curve(18f, 18f, 18f, 20.5f, 20.5f, 20.5f, 20.5f, 22f, 22f, 22f, 22f)
        val all = SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(50))
        assertEquals(2, all.size)
        val later = SetpointWatch.changes(c, 20.5f, t0.plusMinutes(30), t0.plusMinutes(50))
        assertEquals(listOf(SetpointState.TOO_HOT), later.map { it.state })
    }

    @Test
    fun `the last state counts once it has lasted long enough by now`() {
        val c = curve(18f, 18f, 18f, 20.5f)
        assertTrue(SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(16)).isEmpty())
        assertEquals(1, SetpointWatch.changes(c, 20.5f, t0, t0.plusMinutes(25)).size)
    }
}
