package ax.ibr.thermobox.business.climate

import ax.ibr.thermobox.business.hierarchy.HierarchyCache
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.implementations.ClimateServiceImpl
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.DeviceMeasurement
import ax.ibr.thermobox.common.services.EffectiveSetpoint
import ax.ibr.thermobox.common.services.RoomTempAttrService

/**
 * The whole hierarchy (from [HierarchyCache]) with its latest measurements and setpoints, read
 * once: two queries instead of several per element. For the pages that show every element at once (the tree of
 * the overview): the same answers as HierarchyService and ClimateService, computed in memory.
 *
 * A snapshot is the state at the moment it is read: make one per request, never keep it.
 */
class ClimateSnapshot(
    tree: HierarchyCache.Tree = BusinessFactory.getHierarchyCache().tree(),
    roomTemps: RoomTempAttrService = BusinessFactory.getRoomTempAttrService(),
) {
    /** Every element, by id (copies: the cached hierarchy is never handed out). */
    val elements: Map<Long, Room> = tree.elements.mapValues { HierarchyCache.copy(it.value) }

    private val parentIds: Map<Long, Long> = tree.parents
    private val childIds: Map<Long, List<Long>> = tree.children

    private val latest: Map<Long, Temperature> = roomTemps.getLatestMeasurements(elements.values.filterIsInstance<Device>())
    private val setpoints: Map<Long, Setpoint> = roomTemps.getLatestSetpoints(elements.values.filter { it !is Device })

    private val devicesUnder = HashMap<Long, List<Device>>()

    fun parentId(room: Room): Long? = room.id?.let { parentIds[it] }

    fun children(room: Room): List<Room> = room.id?.let { childIds[it] }.orEmpty().mapNotNull { elements[it] }

    /** Ancestors of [room], from the building down to its direct parent. */
    fun path(room: Room): List<Room> {
        val path = ArrayList<Room>()
        var current = parentId(room)?.let { elements[it] }
        while (current != null && path.size < MAX_DEPTH) {
            path.add(0, current)
            current = parentId(current)?.let { elements[it] }
        }
        return path
    }

    /** Every device under [room], at any depth ([room] itself if it is a device). */
    fun devices(room: Room): List<Device> {
        if (room is Device) return listOf(room)
        val id = room.id ?: return emptyList()
        devicesUnder[id]?.let { return it }
        val result = ArrayList<Device>()
        val seen = HashSet<Long>()
        fun walk(s: Room, depth: Int) {
            if (depth > MAX_DEPTH || !seen.add(s.id ?: return)) return
            for (child in children(s)) if (child is Device) result += child else walk(child, depth + 1)
        }
        walk(room, 0)
        return result.also { devicesUnder[id] = it }
    }

    /** Latest measurement of every device under [room] (ClimateService.getMeasurements). */
    fun measurements(room: Room): List<DeviceMeasurement> = devices(room).map { DeviceMeasurement(it, latest[it.id]) }

    /** Setpoint of [room], or of its nearest ancestor (ClimateService.getEffectiveSetpoint). */
    fun effectiveSetpoint(room: Room): EffectiveSetpoint? =
        ClimateServiceImpl.effectiveSetpoint(room, path(room)) { setpoints[it.id] }

    /** Elements of [kind] under [room], at any depth. */
    fun below(room: Room, kind: RoomKind): List<Room> {
        val result = ArrayList<Room>()
        fun walk(s: Room, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (child in children(s)) {
                if (child.kind == kind) result += child
                if (child !is Device) walk(child, depth + 1)
            }
        }
        walk(room, 0)
        return result
    }

    private companion object {
        /** Deepest chain: Building → Floor → Room → Device. */
        const val MAX_DEPTH = 4
    }
}
