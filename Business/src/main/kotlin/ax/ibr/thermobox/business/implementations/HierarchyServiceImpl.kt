package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.hierarchy.HierarchyCache
import ax.ibr.thermobox.business.rules.HierarchyRules
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.common.services.RoomService
import java.io.Serializable

/**
 * With a [cache] (the one of BusinessFactory), parents, children and paths are read in memory
 * ([HierarchyCache]); without, from the services, as the tests do.
 */
class HierarchyServiceImpl(
    private val rooms: RoomService = BusinessFactory.getRoomService(),
    private val links: RoomRoomAttrService = BusinessFactory.getRoomRoomAttrService(),
    private val cache: HierarchyCache? = null,
) : HierarchyService, Serializable {

    private val order = compareBy<Room>({ it.kind }, { it.name?.lowercase() })

    override fun getRoots(): List<Room> {
        cache?.tree()?.let { tree ->
            return tree.elements.values.filter { it.id !in tree.parents }.map { HierarchyCache.copy(it) }.sortedWith(order)
        }
        val attached = links.getAll().mapNotNull { it.roomB.id }.toSet()
        return rooms.getAll().filter { it.id !in attached }.sortedWith(order)
    }

    override fun getChildren(parent: Room): List<Room> {
        cache?.tree()?.let { tree ->
            return tree.children[parent.id].orEmpty().mapNotNull { tree.elements[it] }.map { HierarchyCache.copy(it) }.sortedWith(order)
        }
        return links.getChildren(parent).sortedWith(order)
    }

    override fun getParent(child: Room): Room? {
        cache?.tree()?.let { tree ->
            return tree.parents[child.id]?.let { tree.elements[it] }?.let { HierarchyCache.copy(it) }
        }
        return links.getParent(child)
    }

    override fun getPath(room: Room): List<Room> {
        val path = ArrayList<Room>()
        var current = getParent(room)
        // The rules forbid cycles; the bound only protects against bad data in database.
        while (current != null && path.size < MAX_DEPTH) {
            path.add(0, current)
            current = getParent(current)
        }
        return path
    }

    override fun getDevices(room: Room): List<Device> =
        if (room is Device) listOf(room) else descendants(room).filterIsInstance<Device>()

    override fun getRooms(room: Room): List<Room> =
        descendants(room).filter { it.kind == RoomKind.ROOM }.sortedBy { it.name?.lowercase() }

    /** Everything under [room], at any depth, parents before their children. */
    private fun descendants(room: Room): List<Room> {
        val result = ArrayList<Room>()
        val seen = HashSet<Long?>()
        fun walk(s: Room, depth: Int) {
            if (depth > MAX_DEPTH || !seen.add(s.id)) return
            for (child in getChildren(s)) {
                result.add(child)
                if (child !is Device) walk(child, depth + 1)
            }
        }
        walk(room, 0)
        return result
    }

    override fun setParent(child: Room, parent: Room?) {
        val currentLinks = links.getByRoom(child).filter { it.roomB.id == child.id }
        if (parent != null && currentLinks.size == 1 && currentLinks[0].roomA.id == parent.id) return

        // Check before touching anything, so a refused move keeps the current parent.
        if (parent != null) HierarchyRules.checkLink(parent, child)

        currentLinks.forEach { links.remove(it) }
        if (parent == null) return
        try {
            links.add(RoomRoomAttr(parent, child))
        } catch (e: Exception) {
            currentLinks.forEach { links.add(RoomRoomAttr(it.roomA, it.roomB)) }
            throw e
        }
    }

    private companion object {
        /** Deepest chain: Building → Floor → Room → Device. */
        const val MAX_DEPTH = 4
    }
}
