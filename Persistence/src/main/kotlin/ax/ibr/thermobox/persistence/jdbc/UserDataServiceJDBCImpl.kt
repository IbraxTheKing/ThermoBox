package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.UserDataService

class UserDataServiceJDBCImpl : UserDataService {

    override fun getByUsername(username: String): User? {
        TODO("Not yet implemented")
    }

    override fun getByType(userType: UserType): List<User>? {
        TODO("Not yet implemented")
    }

    override fun add(t: User) {
        TODO("Not yet implemented")
    }

    override fun update(t: User) {
        TODO("Not yet implemented")
    }

    override fun remove(t: User) {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<User> {
        TODO("Not yet implemented")
    }

    override fun getById(id: Long): User? {
        TODO("Not yet implemented")
    }
}