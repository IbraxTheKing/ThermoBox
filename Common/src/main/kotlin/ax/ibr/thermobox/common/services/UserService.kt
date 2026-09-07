package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.utils.services.CrudService

interface UserService : CrudService<User> {

    fun getByUsername(username: String): User?

    fun getByType(userType: UserType): List<User>?

}