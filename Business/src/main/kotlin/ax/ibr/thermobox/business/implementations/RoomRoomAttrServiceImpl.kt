package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.rules.HierarchyRules
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

class RoomRoomAttrServiceImpl(
    private val roomRoomAttrService: RoomRoomAttrService = PersistenceFactory.getRoomRoomAttrDataService()
) : RoomRoomAttrService, Serializable {

    override fun getByRoom(s: Room): List<RoomRoomAttr> {
        return roomRoomAttrService.getByRoom(s)
    }

    override fun getChildren(parent: Room): List<Room> {
        return roomRoomAttrService.getChildren(parent)
    }

    override fun getParent(child: Room): Room? {
        return roomRoomAttrService.getParent(child)
    }

    /** @throws IllegalArgumentException if the link breaks the hierarchy rules. */
    override fun add(t: RoomRoomAttr) {
        verification(t)
        roomRoomAttrService.add(t)
    }

    /** @throws IllegalArgumentException if the link breaks the hierarchy rules. */
    override fun update(t: RoomRoomAttr) {
        verification(t)
        roomRoomAttrService.update(t)
    }

    override fun remove(t: RoomRoomAttr) {
        roomRoomAttrService.remove(t)
    }

    override fun getAll(): List<RoomRoomAttr> {
        return roomRoomAttrService.getAll()
    }

    override fun getById(id: Long): RoomRoomAttr? {
        return roomRoomAttrService.getById(id)
    }

    private fun verification(t: RoomRoomAttr) {
        HierarchyRules.checkLink(t.roomA, t.roomB)

        // A child has a single parent (e.g. a floor belongs to one building only).
        val otherParent = getByRoom(t.roomB)
            .firstOrNull { it.roomB.id == t.roomB.id && it.id != t.id }
            ?.roomA
        require(otherParent == null) {
            "\"${t.roomB.name}\" is already attached to \"${otherParent?.name}\"."
        }
    }
}
