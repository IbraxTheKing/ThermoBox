package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room

/** Business operations on the Building → (Floor) → Room → Device tree. */
interface HierarchyService {

    /** Elements without parent (buildings, plus anything not attached yet), sorted by kind then name. */
    fun getRoots(): List<Room>

    fun getChildren(parent: Room): List<Room>

    fun getParent(child: Room): Room?

    /** Ancestors of [room], from the building down to its direct parent. */
    fun getPath(room: Room): List<Room>

    /** Every box under [room], at any depth ([room] itself if it is a box). */
    fun getDevices(room: Room): List<Device>

    /** Every room (not floor, not device) under [room], at any depth, sorted by name. */
    fun getRooms(room: Room): List<Room>

    /**
     * Attaches [child] to [parent], or detaches it when [parent] is null.
     * Replaces the current parent, if any.
     *
     * @throws IllegalArgumentException if the link breaks the hierarchy rules.
     */
    fun setParent(child: Room, parent: Room?)
}
