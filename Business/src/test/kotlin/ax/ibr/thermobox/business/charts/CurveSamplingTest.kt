package ax.ibr.thermobox.business.charts

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Temperature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.math.sin

class CurveSamplingTest {

    private val start = LocalDateTime.of(2026, 10, 1, 0, 0)

    /** One measurement a minute for [minutes] minutes. */
    private fun curve(minutes: Int, value: (Int) -> Float) = (0 until minutes).map { Measurement(value(it), start.plusMinutes(it.toLong())) }

    @Test
    fun `a month of measurements becomes the number of points asked`() {
        val month = curve(43_200) { (20 + 2 * sin(it / 720.0)).toFloat() }
        val sampled = CurveSampling.sample(month, 500)
        assertEquals(500, sampled.size)
        assertSame(month.first(), sampled.first())
        assertSame(month.last(), sampled.last())
        // In date order, and only original points
        assertTrue(sampled.zipWithNext().all { (a, b) -> a.date!!.isBefore(b.date) })
        assertTrue(sampled.all { s -> month.any { it === s } })
    }

    @Test
    fun `a peak survives the reduction`() {
        // Flat at 20 °C with one reading at 35 °C: an average per bucket would hide it
        val day = curve(1440) { if (it == 777) 35f else 20f }
        val sampled = CurveSampling.sample(day, 50)
        assertTrue(sampled.any { it.value == 35f })
    }

    @Test
    fun `a short curve is left as it is, without empty points`() {
        val short = curve(10) { 20f + it }
        assertEquals(short, CurveSampling.sample(short, 500))
        val withHoles = short + Temperature() + Measurement(1f, start).also { it.value = null }
        assertEquals(short, CurveSampling.sample(withHoles, 500))
        assertEquals(emptyList<Temperature>(), CurveSampling.sample(emptyList(), 3))
    }

    @Test
    fun `at least three points`() {
        assertThrows(IllegalArgumentException::class.java) { CurveSampling.sample(curve(10) { 20f }, 2) }
        assertEquals(3, CurveSampling.sample(curve(10) { 20f + it }, 3).size)
    }
}
