package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.utils.services.CrudService

interface RoomRoomAttrService : CrudService<RoomRoomAttr> {

    fun getByRoom(s: Room) : List<RoomRoomAttr>

    /** Direct children of [parent] (see [RoomRoomAttr] for the link direction). */
    fun getChildren(parent: Room): List<Room>

    /** Direct parent of [child], or null if it is not attached. */
    fun getParent(child: Room): Room?
}