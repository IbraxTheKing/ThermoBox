package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Temperature
import java.io.Serializable
import java.time.Instant

/** Latest measure of a box, null if it never reported. */
data class DeviceMeasurement(val device: Device, val measurement: Temperature?) : Serializable

/** Measures of a box over a period, oldest first. */
data class DeviceHistory(val device: Device, val measurements: List<Temperature>) : Serializable

/**
 * The setpoint that applies to an element, and the element it was set on ([source]): the
 * element itself, or its nearest ancestor that has one ([inherited]).
 */
data class EffectiveSetpoint(val setpoint: Setpoint, val source: Room, val inherited: Boolean) : Serializable

/** Outcome of [ClimateService.sendSetpoint]. */
data class SetpointDispatch(
    /** Floors and rooms under the target that kept their own setpoint (not forced). */
    val kept: List<EffectiveSetpoint>,
    /** Floors and rooms under the target whose own setpoint was replaced (forced). */
    val replaced: List<Room>,
    /** Devices the setpoint was sent to. */
    val devices: List<Device>,
    /** Boxes it could not be sent to. */
    val failures: List<Device>,
) : Serializable

/**
 * Temperatures seen at the level of any element of the hierarchy: a room (or a floor, a
 * building) aggregates the devices it contains, at any depth.
 */
interface ClimateService {

    /** Latest measure of every box under [room]. */
    fun getMeasurements(room: Room): List<DeviceMeasurement>

    /** Average of the latest measure of every box under [room], null if none has reported. */
    fun getAverage(room: Room): Float?

    /** Setpoint of [room], or else of its nearest ancestor (a room follows its building's). */
    fun getSetpoint(room: Room): Setpoint? = getEffectiveSetpoint(room)?.setpoint

    fun getEffectiveSetpoint(room: Room): EffectiveSetpoint?

    /**
     * Floors and rooms under [room], at any depth, that have their own setpoint: a setpoint
     * sent to [room] leaves them alone unless forced. Devices never count.
     */
    fun getOwnSetpoints(room: Room): List<EffectiveSetpoint>

    fun getHistories(room: Room, start: Instant, end: Instant): List<DeviceHistory>

    /**
     * Average of the boxes over time: one point per measure, each box counting with its last
     * known value at that moment (boxes don't report at the same time).
     */
    fun getAverageHistory(room: Room, start: Instant, end: Instant): List<Temperature>

    /** Setpoints over the period, taken where the current one comes from (inherited or not). */
    fun getSetpoints(room: Room, start: Instant, end: Instant): List<Setpoint>

    /**
     * Saves [value] as the setpoint of [room], which the floors and rooms under it without a
     * setpoint of their own then follow, and sends it to their devices. Those with their own
     * setpoint keep it, with their devices, unless [force]: then [value] replaces it too.
     *
     * @throws Exception if the value is rejected (too hot, impossible); nothing is saved then.
     */
    fun sendSetpoint(room: Room, value: Float, force: Boolean = false): SetpointDispatch
}
