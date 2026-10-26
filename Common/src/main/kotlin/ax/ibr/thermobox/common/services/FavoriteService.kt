package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

interface FavoriteService : CrudService<Favorite> {

    fun getByUser(user: User): List<Favorite>

    fun getByRoom(room: Room): List<Favorite>
}
