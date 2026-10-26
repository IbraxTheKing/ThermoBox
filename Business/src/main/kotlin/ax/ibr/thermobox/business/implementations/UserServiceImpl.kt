package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.AccessService
import ax.ibr.thermobox.common.services.FavoriteService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import javax.naming.InvalidNameException


// The data service is a constructor parameter so tests can pass a mock.
class UserServiceImpl(
    private val userService: UserService = PersistenceFactory.getUserDataService()
) : UserService, Serializable {

    // Only needed by remove(); lazy so tests of the other methods don't open the database.
    private val favorites: FavoriteService by lazy { PersistenceFactory.getFavoriteDataService() }
    private val access: AccessService by lazy { BusinessFactory.getAccessService() }

    override fun getByUsername(username: String): User? {
        return userService.getByUsername(username)
    }

    override fun getByType(userType: UserType): List<User>? {
        return userService.getByType(userType)
    }

    override fun add(t: User) {
        hashPassword(t)
        if (!UserVerification.verifyUsername(t.username!!))
            throw InvalidNameException("Username should only contains letters.")
        userService.add(t)
        // New accounts see nothing until an ADMIN grants them accesses (no excessive rights by default).
        if (t.id != null) access.initialize(t)
    }

    override fun update(t: User) {
        // An ADMIN turned into a MANAGER / VIEWER doesn't keep a view on everything.
        val before = t.id?.let { userService.getById(it) }?.type
        if (before == UserType.ADMIN && t.type != UserType.ADMIN) access.initialize(t)
        hashPassword(t)
        if (!UserVerification.verifyUsername(t.username!!))
            throw InvalidNameException("Username should only contains letters.")
        userService.update(t)
    }

    /** Deletes the user's favourites first, or the foreign key blocks the delete. */
    override fun remove(t: User) {
        // A user never saved (no id) has no favourites: no need to reach the database for them.
        if (t.id != null) {
            favorites.getByUser(t).forEach { favorites.remove(it) }
            access.forgetUser(t)
        }
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

private object UserVerification {

    fun verifyUsername(username: String): Boolean {
        // Matches only lowercase and uppercase English letters (A-Z, a-z)
        // The username cannot be empty
        return username.matches(Regex("^[a-zA-Z]+$"))
    }
}