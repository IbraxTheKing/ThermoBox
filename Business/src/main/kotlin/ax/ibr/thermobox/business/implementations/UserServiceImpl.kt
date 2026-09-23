package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable


class UserServiceImpl : UserService, Serializable {

    private val userService: UserService = PersistenceFactory().getUserDataService()

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

    override fun remove(t: User) {
        userService.remove(t)
    }

    override fun getAll(): List<User> {
        return userService.getAll()
    }

    override fun getById(id: Long): User? {
        return userService.getById(id)
    }

}