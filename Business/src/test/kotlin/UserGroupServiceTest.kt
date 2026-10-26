package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class UserGroupServiceTest {

    private val s1 = Room("S1").apply { id = 1 }
    private val x1 = Device("X1").apply { id = 2 }
    private val alice = User("alice", "x", UserType.VIEWER).apply { id = 10 }

    private val groups = ArrayList<UserGroup>()
    private val replacements = ArrayList<Triple<Long?, Collection<Long>, Collection<Long>>>()

    private val data = Proxy.newProxyInstance(UserGroupDataService::class.java.classLoader, arrayOf(UserGroupDataService::class.java)) { _, m, a ->
        when (m.name) {
            "getByName" -> groups.firstOrNull { it.name == a[0] }
            "getById" -> groups.firstOrNull { it.id == a[0] }
            "add" -> (a[0] as UserGroup).also { it.id = groups.size + 1L; groups += it }.let { Unit }
            "update" -> Unit
            "replace" -> { @Suppress("UNCHECKED_CAST") replacements += Triple((a[0] as UserGroup).id, a[1] as Collection<Long>, a[2] as Collection<Long>); Unit }
            else -> throw UnsupportedOperationException(m.name)
        }
    } as UserGroupDataService

    private val rooms = Proxy.newProxyInstance(RoomService::class.java.classLoader, arrayOf(RoomService::class.java)) { _, m, a ->
        when (m.name) {
            "getById" -> listOf(s1, x1).firstOrNull { it.id == a[0] }
            else -> throw UnsupportedOperationException(m.name)
        }
    } as RoomService

    private val service = UserGroupServiceImpl(data, rooms)

    @Test
    fun `creation with members and elements, unique name`() {
        val g = service.save(null, "  Reception  ", null, listOf(alice), listOf(s1))
        assertEquals("Reception", g.name)
        assertEquals(Triple(g.id, listOf(10L), listOf(1L)), replacements.single().let { Triple(it.first, it.second.toList(), it.third.toList()) })
        assertThrows(IllegalArgumentException::class.java) { service.save(null, "Reception", null, emptyList(), emptyList()) }
        // Same name for the same group: fine (renaming nothing)
        service.save(g, "Reception", "Hall", emptyList(), emptyList())
    }

    @Test
    fun `name required, no device, known element`() {
        assertThrows(IllegalArgumentException::class.java) { service.save(null, " ", null, emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { service.save(null, "B", null, emptyList(), listOf(x1)) }
        assertThrows(IllegalArgumentException::class.java) { service.save(null, "C", null, emptyList(), listOf(Room("?").apply { id = 99 })) }
    }
}
