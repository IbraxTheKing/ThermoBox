package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.utils.services.CrudService

interface UserService : CrudService<User> {

    fun getByUsername(username: String): User?

    fun getByType(userType: UserType): List<User>?

    /** The account with this address (case ignored), null if none. */
    fun getByEmail(email: String): User? = getAll().firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

}