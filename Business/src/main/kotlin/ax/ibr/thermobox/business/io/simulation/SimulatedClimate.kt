package ax.ibr.thermobox.business.io.simulation

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plausible temperatures for fake devices, shared by the simulated driver and the database seeder
 * so the seeded history and the live readings look alike.
 *
 * A device tends towards its setpoint, shifted by its spot in the room and by the time of day.
 */
object SimulatedClimate {

    const val DEFAULT_SETPOINT = 20f

    /** Stable offset of a box from its room (near a window, a radiator…): -0.8 to +0.8 °C. */
    fun offset(deviceName: String): Float = (deviceName.hashCode().mod(17) - 8) / 10f

    /** Daily swing: about -1 °C around 5 a.m., +1 °C around 5 p.m. */
    fun dailyCycle(date: LocalDateTime): Float {
        val hour = date.hour + date.minute / 60.0
        return sin((hour - 11) / 24 * 2 * PI).toFloat()
    }

    /** Temperature the box settles at under [setpoint] at [date]. */
    fun target(setpoint: Float, deviceName: String, date: LocalDateTime): Float =
        setpoint + offset(deviceName) + dailyCycle(date)

    /** Next reading: moves part of the way from [current] towards [target], with sensor noise. */
    fun next(current: Float, target: Float, random: Random = Random): Float =
        current + (target - current) * 0.3f + (random.nextFloat() - 0.5f) * 0.3f
}
