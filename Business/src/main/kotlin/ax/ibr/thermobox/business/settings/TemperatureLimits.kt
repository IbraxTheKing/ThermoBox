package ax.ibr.thermobox.business.settings

import ax.ibr.thermobox.business.update.UpdateConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** A closed range of temperatures, in Celsius. */
data class Range(val min: Float, val max: Float) {
    operator fun contains(value: Float): Boolean = value in min..max
}

/**
 * Temperatures the server accepts, chosen by an ADMIN (GET / PUT /api/v2/server/limits):
 * - [measurement]: values a device may report, or an ADMIN may add by hand. A reading outside is
 *   taken for a sensor error and dropped;
 * - [setpoint]: setpoints that may be saved and sent to the devices.
 */
data class TemperatureLimits(val measurement: Range, val setpoint: Range) {

    init {
        for ((name, r) in listOf("measurement" to measurement, "setpoint" to setpoint)) {
            require(r.min.isFinite() && r.max.isFinite()) { "$name: min and max must be numbers" }
            require(r.min < r.max) { "$name: min must be lower than max" }
            require(r.min >= ABSOLUTE.min && r.max <= ABSOLUTE.max) {
                "$name: limits must stay between ${ABSOLUTE.min} and ${ABSOLUTE.max} °C"
            }
        }
    }

    companion object {
        /** No limit can be set beyond this (declared first: [DEFAULT] is checked against it). */
        val ABSOLUTE = Range(-100f, 200f)

        /** The limits of the versions before 26.12, used until an ADMIN changes them. */
        val DEFAULT = TemperatureLimits(Range(-10f, 50f), Range(-10f, 50f))
    }
}

/**
 * Where the [TemperatureLimits] are kept: `temperature-limits.properties` in the data folder
 * (THERMOBOX_DATA_DIR), read again when the file changes.
 */
class TemperatureLimitsStore(private val directory: Path) {

    private val file: Path get() = directory.resolve(FILE)

    @Volatile private var cache: Pair<Long, TemperatureLimits>? = null

    fun get(): TemperatureLimits {
        val modified = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(-1L)
        cache?.let { (date, limits) -> if (date == modified) return limits }
        val limits = if (modified < 0) TemperatureLimits.DEFAULT else read()
        cache = modified to limits
        return limits
    }

    /** @throws IllegalArgumentException for inconsistent limits (nothing is saved then) */
    @Synchronized
    fun set(limits: TemperatureLimits) {
        val p = Properties()
        p.setProperty("measurement.min", limits.measurement.min.toString())
        p.setProperty("measurement.max", limits.measurement.max.toString())
        p.setProperty("setpoint.min", limits.setpoint.min.toString())
        p.setProperty("setpoint.max", limits.setpoint.max.toString())
        Files.createDirectories(directory)
        // Written aside then moved: a reader never sees half a file
        val tmp = file.resolveSibling("$FILE.tmp")
        Files.newBufferedWriter(tmp).use { p.store(it, "ThermoBox: temperatures accepted, chosen by an administrator (Celsius)") }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cache = null
    }

    /** Back to [TemperatureLimits.DEFAULT]. */
    @Synchronized
    fun reset() {
        Files.deleteIfExists(file)
        cache = null
    }

    // A damaged file falls back to the defaults, value by value, rather than blocking every measurement.
    private fun read(): TemperatureLimits {
        val p = Properties()
        runCatching { Files.newBufferedReader(file).use { p.load(it) } }
        fun v(key: String, default: Float) = p.getProperty(key)?.trim()?.toFloatOrNull() ?: default
        val d = TemperatureLimits.DEFAULT
        return runCatching {
            TemperatureLimits(
                Range(v("measurement.min", d.measurement.min), v("measurement.max", d.measurement.max)),
                Range(v("setpoint.min", d.setpoint.min), v("setpoint.max", d.setpoint.max)),
            )
        }.getOrDefault(d)
    }

    companion object {
        const val FILE = "temperature-limits.properties"

        val instance: TemperatureLimitsStore by lazy {
            TemperatureLimitsStore(runCatching { UpdateConfig.fromEnvironment().directory }
                .getOrElse { Path.of(System.getProperty("user.home"), ".thermobox") })
        }
    }
}
