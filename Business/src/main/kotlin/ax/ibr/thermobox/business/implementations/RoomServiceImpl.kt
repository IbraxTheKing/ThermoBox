package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.services.FavoriteService
import ax.ibr.thermobox.common.services.RoomImageService
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import jakarta.transaction.Transactional

// The data service is a constructor parameter so tests can pass a mock.
class RoomServiceImpl(
    private val roomService: RoomService = PersistenceFactory.getRoomDataService()
) : RoomService, Serializable {

    // Only needed by remove(); lazy so tests of the other methods don't open the database.
    private val links: RoomRoomAttrService by lazy { PersistenceFactory.getRoomRoomAttrDataService() }
    private val access by lazy { PersistenceFactory.getAccessDataService() }
    private val history: RoomTempAttrService by lazy { PersistenceFactory.getRoomTempAttrDataService() }
    private val favorites: FavoriteService by lazy { PersistenceFactory.getFavoriteDataService() }
    private val images: RoomImageService by lazy { PersistenceFactory.getRoomImageDataService() }

    override fun getAllByType(c: Room) : List<Room> {
        return roomService.getAllByType(c)
    }

    override fun getByName(name: String): Room? {
        return roomService.getByName(name)
    }

    @Transactional
    override fun add(t: Room) {
        roomService.add(t)
    }

    @Transactional
    override fun update(t: Room) {
        roomService.update(t)
    }

    @Transactional
    /**
     * Deletes the element with its history, its image and the favourites pointing to it. Its links go too:
     * its children are kept but become unattached. Without this the foreign keys would block the delete.
     */
    override fun remove(t: Room) {
        if (t.id != null) {
            favorites.getByRoom(t).forEach { favorites.remove(it) }
            images.getByRoom(t)?.let { images.remove(it) }
            access.deleteForRoom(t)
            // …and the groups that gave access to it
            PersistenceFactory.getUserGroupDataService().forgetRoom(t)
        }
        links.getByRoom(t).forEach { links.remove(it) }
        history.getByRoom(t).orEmpty().forEach { history.remove(it) }
        roomService.remove(t)
    }

    override fun getAll(): List<Room> {
        return roomService.getAll()
    }

    override fun getById(id: Long): Room? {
        return roomService.getById(id)
    }
}
