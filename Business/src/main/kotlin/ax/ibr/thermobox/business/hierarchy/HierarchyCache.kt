package ax.ibr.thermobox.business.hierarchy

import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.common.services.RoomService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The buildings, floors, rooms and devices and their links, kept in memory: every request asks
 * for parents, children, paths and contained devices (access rights, tree, curves), and the
 * hierarchy hardly ever changes. Read again from the database:
 * - at once after a change made through the business services ([invalidate], called by
 *   RoomServiceImpl and RoomRoomAttrServiceImpl);
 * - at the latest after [ttl], for the changes made elsewhere (another server sharing the
 *   database, a script).
 *
 * The rooms handed out are copies: a caller may change them without touching the cache.
 */
class HierarchyCache(
    private val rooms: RoomService,
    private val links: RoomRoomAttrService,
    private val ttl: Duration = Duration.ofSeconds(30),
    private val clock: Clock = Clock.systemUTC(),
) {

    /** One state of the hierarchy, never changed once built. */
    class Tree(elements: Collection<Room>, linkPairs: List<Pair<Long, Long>>) {
        /** Every element by id (the cache's own instances: copy them before handing them out). */
        val elements: Map<Long, Room> = elements.filter { it.id != null }.associateBy { it.id!! }
        /** Child id → parent id; a child has one parent, the first link wins (as getParent). */
        val parents: Map<Long, Long> = linkPairs.filter { (c, p) -> c in this.elements && p in this.elements }.reversed().toMap()
        /** Parent id → child ids. */
        val children: Map<Long, List<Long>> = parents.entries.groupBy({ it.value }, { it.key })
    }

    private class Loaded(val tree: Tree, val version: Long, val at: Instant)

    @Volatile
    private var loaded: Loaded? = null

    /** The current hierarchy. */
    fun tree(): Tree {
        val current = loaded
        val version = VERSION.get()
        if (current != null && current.version == version && clock.instant().isBefore(current.at.plus(ttl))) return current.tree
        synchronized(this) {
            val again = loaded
            if (again != null && again.version == VERSION.get() && clock.instant().isBefore(again.at.plus(ttl))) return again.tree
            // The version read before loading: a change during the load makes the next call reload
            val v = VERSION.get()
            val tree = Tree(
                rooms.getAll(),
                links.getAll().mapNotNull { l -> l.roomB.id?.let { c -> l.roomA.id?.let { p -> c to p } } },
            )
            loaded = Loaded(tree, v, clock.instant())
            return tree
        }
    }

    companion object {
        private val VERSION = AtomicLong()

        /** After a change of an element or a link: every cache reads the hierarchy again. */
        fun invalidate() {
            VERSION.incrementAndGet()
        }

        /** A copy of [room], of the same kind. */
        fun copy(room: Room): Room = when (room) {
            is Building -> Building(room.name)
            is Floor -> Floor(room.name)
            is Device -> Device(room.name)
            else -> Room(room.name)
        }.also { it.id = room.id }
    }
}
