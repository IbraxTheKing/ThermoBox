package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.FavoriteService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

class FavoriteServiceImpl(
    private val favoriteService: FavoriteService = PersistenceFactory.getFavoriteDataService()
) : FavoriteService, Serializable {

    override fun getByUser(user: User): List<Favorite> = favoriteService.getByUser(user)

    override fun getByRoom(room: Room): List<Favorite> = favoriteService.getByRoom(room)

    /** Does nothing if the room is already a favorite of this user. */
    override fun add(t: Favorite) {
        if (getByUser(t.user).none { it.room.id == t.room.id }) favoriteService.add(t)
    }

    override fun update(t: Favorite) = favoriteService.update(t)

    override fun remove(t: Favorite) = favoriteService.remove(t)

    override fun getAll(): List<Favorite> = favoriteService.getAll()

    override fun getById(id: Long): Favorite? = favoriteService.getById(id)
}
