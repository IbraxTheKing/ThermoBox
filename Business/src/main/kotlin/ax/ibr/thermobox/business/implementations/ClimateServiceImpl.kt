package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.rules.SetpointRules
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.ClimateService
import ax.ibr.thermobox.common.services.EffectiveSetpoint
import ax.ibr.thermobox.common.services.SetpointDispatch
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.DeviceHistory
import ax.ibr.thermobox.common.services.DeviceMeasurement
import ax.ibr.thermobox.common.services.RoomTempAttrService
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class ClimateServiceImpl(
    private val hierarchy: HierarchyService = BusinessFactory.getHierarchyService(),
    private val roomTemps: RoomTempAttrService = BusinessFactory.getRoomTempAttrService(),
    private val driver: () -> ProtocolDriver = { BusinessFactory.getDriver() }
) : ClimateService, Serializable {

    override fun getMeasurements(room: Room): List<DeviceMeasurement> {
        val devices = hierarchy.getDevices(room)
        // One query for every device
        val latest = roomTemps.getLatestMeasurements(devices)
        return devices.map { DeviceMeasurement(it, latest[it.id]) }
    }

    override fun getAverage(room: Room): Float? =
        getMeasurements(room).mapNotNull { it.measurement?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    override fun getEffectiveSetpoint(room: Room): EffectiveSetpoint? =
        effectiveSetpoint(room, hierarchy.getPath(room)) { roomTemps.getSetpointFromRoom(it) }

    // A device's own setpoint is only the last one it received: it never counts as a choice to keep.
    override fun getOwnSetpoints(room: Room): List<EffectiveSetpoint> {
        val owned = ArrayList<EffectiveSetpoint>()
        fun walk(s: Room, followed: Setpoint?, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (child in hierarchy.getChildren(s)) {
                if (child is Device) continue
                val own = roomTemps.getSetpointFromRoom(child)
                val differs = own != null && !aligned(own, followed)
                if (differs) owned.add(EffectiveSetpoint(own!!, child, inherited = false))
                walk(child, if (differs) own else followed, depth + 1)
            }
        }
        walk(room, getSetpoint(room), 0)
        return owned
    }

    /**
     * Every measurement up to [HOURLY_FROM]; past it, hourly averages: a week of a device is 168
     * points instead of a thousand or more, which a chart could not show apart anyway.
     */
    override fun getHistories(room: Room, start: Instant, end: Instant): List<DeviceHistory> =
        histories(room, start, end, hourly = Duration.between(start, end) > HOURLY_FROM)

    override fun getRawHistories(room: Room, start: Instant, end: Instant): List<DeviceHistory> =
        histories(room, start, end, hourly = false)

    /** Every room's devices in one query, then each room's average in memory. */
    override fun getAverageHistories(rooms: Collection<Room>, start: Instant, end: Instant, raw: Boolean): Map<Long, List<Temperature>> {
        val devicesOf = rooms.filter { it.id != null }.associate { it.id!! to hierarchy.getDevices(it) }
        val devices = devicesOf.values.flatten().distinctBy { it.id }
        val hourly = !raw && Duration.between(start, end) >= AVERAGES_HOURLY_FROM
        val series = if (hourly) roomTemps.getHourlyMeasurementsOf(devices, start, end) else roomTemps.getMeasurementsOf(devices, start, end)
        return devicesOf.mapValues { (_, list) -> movingAverage(list.map { DeviceHistory(it, series[it.id].orEmpty()) }) }
    }

    private fun histories(room: Room, start: Instant, end: Instant, hourly: Boolean): List<DeviceHistory> {
        val devices = hierarchy.getDevices(room)
        // Every device in one query
        val series = if (hourly) roomTemps.getHourlyMeasurementsOf(devices, start, end) else roomTemps.getMeasurementsOf(devices, start, end)
        return devices.map { DeviceHistory(it, series[it.id].orEmpty()) }
    }

    override fun getAverageHistory(room: Room, start: Instant, end: Instant): List<Temperature> =
        movingAverage(getHistories(room, start, end))

    override fun getSetpoints(room: Room, start: Instant, end: Instant): List<Setpoint> {
        val source = getEffectiveSetpoint(room)?.source ?: room
        return roomTemps.getSetpointsFromRoomTimed(source, start, end)
    }

    override fun sendSetpoint(room: Room, value: Float, force: Boolean): SetpointDispatch {
        SetpointRules.check(value)
        val previous = getSetpoint(room)
        val date = LocalDateTime.now()
        roomTemps.add(RoomTempAttr(room, Setpoint(value, date)))

        val kept = ArrayList<EffectiveSetpoint>()
        val replaced = ArrayList<Room>()
        val devices = ArrayList<Device>()
        // followed: the setpoint the current element followed before this one.
        fun walk(s: Room, followed: Setpoint?, depth: Int) {
            if (s is Device) {
                devices.add(s)
                return
            }
            if (depth > MAX_DEPTH) return
            for (child in hierarchy.getChildren(s)) {
                val own = if (child is Device) null else roomTemps.getSetpointFromRoom(child)
                if (own != null) {
                    val differs = !aligned(own, followed)
                    if (differs && !force) {
                        // It keeps its setpoint, and so do its devices.
                        kept.add(EffectiveSetpoint(own, child, inherited = false))
                        continue
                    }
                    // Same setpoint and date as the parent: it keeps following it from now on.
                    roomTemps.add(RoomTempAttr(child, Setpoint(value, date)))
                    if (differs) replaced.add(child)
                    walk(child, if (differs) own else followed, depth + 1)
                    continue
                }
                walk(child, followed, depth + 1)
            }
        }
        walk(room, previous, 0)

        // A device that fails must not prevent the others from receiving the setpoint.
        val failures = devices.filter { device ->
            runCatching { driver().sendSetpoint(device, Setpoint(value, date)) }.isFailure
        }
        return SetpointDispatch(kept, replaced, devices, failures)
    }

    companion object {
        /** Longer periods are drawn from hourly averages (getHistories). */
        val HOURLY_FROM: Duration = Duration.ofDays(2)

        /**
         * The same for the average curves of several rooms at once (getAverageHistories: the
         * mini-curves of a dashboard, 24 hours of dozens of rooms). A trend at a glance: 24 hourly
         * points per room are read instead of every measurement of every device (about 140 per
         * device and per day), without a join, which is what weighed on the server under load.
         */
        val AVERAGES_HOURLY_FROM: Duration = Duration.ofHours(12)

        /**
         * A setpoint saved by a forced send carries the value and the date of its ancestor's: the
         * element then still follows the ancestor rather than having a setpoint of its own.
         */
        fun aligned(own: Setpoint, followed: Setpoint?): Boolean =
            followed != null && own.date == followed.date && own.value == followed.value

        /**
         * The setpoint that applies to [room]: its own, otherwise the one of its nearest ancestor
         * ([path]: from the building down to its parent) that has one. [setpointOf] gives the own
         * setpoint of an element, asked only as far as needed.
         */
        fun effectiveSetpoint(room: Room, path: List<Room>, setpointOf: (Room) -> Setpoint?): EffectiveSetpoint? {
            val own = setpointOf(room)
            // Nearest ancestor first: a floor's setpoint wins over its building's.
            var inherited: EffectiveSetpoint? = null
            for (ancestor in path.asReversed()) {
                val c = setpointOf(ancestor) ?: continue
                inherited = EffectiveSetpoint(c, ancestor, inherited = true)
                break
            }
            return when {
                own == null -> inherited
                // Set by a forced setpoint of the ancestor: the room still follows it.
                inherited != null && aligned(own, inherited.setpoint) -> inherited
                else -> EffectiveSetpoint(own, room, inherited = false)
            }
        }

        /** Deepest chain: Building → Floor → Room → Device. */
        private const val MAX_DEPTH = 4

        /**
         * Average of several series over time. At each timestamp of any series, every series
         * counts with its last value so far; series that haven't started yet are left out.
         */
        fun movingAverage(histories: List<DeviceHistory>): List<Temperature> {
            val dates = histories.flatMap { h -> h.measurements.mapNotNull { it.date } }.distinct().sorted()
            val series = histories.map { h -> h.measurements.filter { it.date != null && it.value != null } }
            val positions = IntArray(series.size)
            val latest = arrayOfNulls<Float>(series.size)

            return dates.map { date ->
                series.forEachIndexed { i, measurements ->
                    while (positions[i] < measurements.size && !measurements[positions[i]].date!!.isAfter(date)) {
                        latest[i] = measurements[positions[i]].value
                        positions[i]++
                    }
                }
                Temperature(latest.filterNotNull().average().toFloat(), date)
            }
        }
    }
}
