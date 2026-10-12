package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.FavoriService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable


// The data service is a constructor parameter so tests can pass a mock.
class UserServiceImpl(
    private val userService: UserService = PersistenceFactory.getUserDataService()
) : UserService, Serializable {

    // Only needed by remove(); lazy so tests of the other methods don't open the database.
    private val favoris: FavoriService by lazy { PersistenceFactory.getFavoriDataService() }

    override fun getByUsername(username: String): User? {
        return userService.getByUsername(username)
    }

    override fun getByType(userType: UserType): List<User>? {
        return userService.getByType(userType)
    }

    override fun add(t: User) {
        userService.add(t)
    }

    override fun update(t: User) {
        userService.update(t)
    }

    /** Deletes the user's favourites first, or the foreign key blocks the delete. */
    override fun remove(t: User) {
        favoris.getByUser(t).forEach { favoris.remove(it) }
        userService.remove(t)
    }

    override fun getAll(): List<User> {
        return userService.getAll()
    }

    override fun getById(id: Long): User? {
        return userService.getById(id)
    }

}