package ax.ibr.thermobox.business.users

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.UserService

/** In-memory accounts for the tests: ids given on add, [refuse] makes add fail for some names. */
class FakeUsers(private val refuse: (User) -> Boolean = { false }) : UserService {
    val lines = mutableListOf<User>()
    private var next = 1L
    override fun getByUsername(username: String) = lines.firstOrNull { it.username.equals(username, ignoreCase = true) }
    override fun getByType(userType: UserType) = lines.filter { it.type == userType }
    override fun add(t: User) {
        if (refuse(t)) throw IllegalStateException("refused")
        t.id = next++
        lines += t
    }
    override fun update(t: User) { lines.replaceAll { if (it.id == t.id) t else it } }
    override fun remove(t: User) { lines.removeIf { it.id == t.id } }
    override fun getAll() = lines.toList()
    override fun getById(id: Long) = lines.firstOrNull { it.id == id }
}
