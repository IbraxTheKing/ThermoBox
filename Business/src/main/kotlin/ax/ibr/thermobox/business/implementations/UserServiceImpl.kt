package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.security.PasswordHasher
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
        hashPassword(t)
        userService.add(t)
    }

    override fun update(t: User) {
        hashPassword(t)
        userService.update(t)
    }

    /** Deletes the user's favourites first, or the foreign key blocks the delete. */
    override fun remove(t: User) {
        // A user never saved (no id) has no favourites: no need to reach the database for them.
        if (t.id != null) favoris.getByUser(t).forEach { favoris.remove(it) }
        userService.remove(t)
    }

    override fun getAll(): List<User> {
        return userService.getAll()
    }

    override fun getById(id: Long): User? {
        return userService.getById(id)
    }

    /**
     * Only BCrypt hashes reach the database. Every write goes through here (JSF, REST, seeder),
     * so a password that is not a hash yet (new account or changed password) is hashed, and one
     * that already is (user re-saved unchanged) is kept as is.
     *
     * @throws IllegalArgumentException if the password is longer than BCrypt allows.
     */
    private fun hashPassword(user: User) {
        val password = user.password ?: return
        if (!PasswordHasher.isHash(password)) user.password = PasswordHasher.hash(password)
    }
}