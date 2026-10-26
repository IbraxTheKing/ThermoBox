package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.persistence.dataservices.AccessDataService
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime

class AccessServiceTest {

    private fun <T : Room> T.withId(id: Long) = apply { this.id = id }

    // Building A → Floor A1 → rooms S1 (device X1), S2; Building B → room S3; room S4 not attached.
    private val a = Building("A").withId(1)
    private val a1 = Floor("A1").withId(2)
    private val s1 = Room("S1").withId(3)
    private val s2 = Room("S2").withId(4)
    private val x1 = Device("X1").withId(5)
    private val b = Building("B").withId(6)
    private val s3 = Room("S3").withId(7)
    private val s4 = Room("S4").withId(8)
    private val everyElement = listOf(a, a1, s1, s2, x1, b, s3, s4)
    private val children = mapOf(1L to listOf(a1), 2L to listOf(s1, s2), 3L to listOf(x1), 6L to listOf(s3))
    private val parents = children.flatMap { (p, l) -> l.map { it.id!! to everyElement.first { s -> s.id == p } } }.toMap()

    private val hierarchy = object : HierarchyService {
        override fun getRoots() = listOf(a, b, s4)
        override fun getChildren(parent: Room) = children[parent.id].orEmpty()
        override fun getParent(child: Room) = parents[child.id]
        override fun getPath(room: Room): List<Room> = generateSequence(parents[room.id]) { parents[it.id] }.toList().asReversed()
        override fun getDevices(room: Room) = emptyList<Device>()
        override fun getRooms(room: Room) = emptyList<Room>()
        override fun setParent(child: Room, parent: Room?) = Unit
    }

    /** The accesses tables, in memory. */
    private class FakeAccess : AccessDataService {
        val lines = mutableListOf<Access>()
        val restrictedIds = mutableSetOf<Long>()
        override fun getByUser(user: User) = lines.filter { it.user.id == user.id }
        override fun isRestricted(user: User) = user.id in restrictedIds
        override fun replace(user: User, restricted: Boolean, roomIds: Collection<Long>) {
            lines.removeIf { it.user.id == user.id }
            restrictedIds -= user.id!!
            if (restricted) {
                restrictedIds += user.id!!
                roomIds.forEach { lines += Access(user, Room().apply { id = it }) }
            }
        }
        override fun deleteForRoom(room: Room) { lines.removeIf { it.room.id == room.id } }
        override fun deleteForUser(user: User) { lines.removeIf { it.user.id == user.id }; restrictedIds -= user.id!! }
        override fun add(t: Access) { lines += t }
        override fun update(t: Access) = Unit
        override fun remove(t: Access) { lines -= t }
        override fun getAll() = lines.toList()
        override fun getById(id: Long) = lines.firstOrNull { it.id == id }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stub(type: Class<T>, responses: Map<String, (Array<Any?>) -> Any?>): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, args ->
            val response = responses[m.name] ?: throw UnsupportedOperationException(m.name)
            response(args ?: emptyArray())
        } as T

    private val rooms = stub(RoomService::class.java, mapOf("getById" to { args -> everyElement.firstOrNull { it.id == args[0] } }))

    private val m1 = Measurement(20f, LocalDateTime.now()).apply { id = 100 }
    private val m3 = Measurement(22f, LocalDateTime.now()).apply { id = 101 }
    private val roomTemps = stub(RoomTempAttrService::class.java, mapOf("getAll" to { _ ->
        listOf(RoomTempAttr(x1, m1), RoomTempAttr(s3, m3))
    }))

    private val data = FakeAccess()
    /** Groups in memory: user id → elements of their groups. */
    private val groupElements = HashMap<Long, List<Room>>()
    private val fakeGroups = Proxy.newProxyInstance(UserGroupDataService::class.java.classLoader, arrayOf(UserGroupDataService::class.java)) { _, m, args ->
        when (m.name) {
            "getElementsOfGroupsOf" -> groupElements[(args[0] as User).id].orEmpty()
            "forgetRoom", "forgetUser" -> Unit
            else -> throw UnsupportedOperationException(m.name)
        }
    } as UserGroupDataService

    private val service = AccessServiceImpl(data, hierarchy, rooms, groups = { fakeGroups }) { roomTemps }

    private fun user(id: Long, type: UserType) = User("u$id", "x", type).apply { this.id = id }

    @Test
    fun `without restriction, an existing account sees everything`() {
        val p = service.scope(user(10, UserType.VIEWER))
        assertTrue(p.all)
        assertTrue(p.canSee(s4))
    }

    @Test
    fun `a new account sees nothing, an administrator always everything`() {
        val u = user(11, UserType.MANAGER)
        service.initialize(u)
        assertFalse(service.scope(u).canSee(a))
        assertEquals(0, service.scope(u).accessibleCount)

        val admin = user(12, UserType.ADMIN)
        data.restrictedIds += 12L
        assertTrue(service.scope(admin).all)
        assertThrows(IllegalArgumentException::class.java) { service.define(admin, true, listOf(s1)) }
    }

    @Test
    fun `an access covers the content, ancestors are only visible`() {
        val u = user(13, UserType.VIEWER)
        service.define(u, true, listOf(a1))
        val p = service.scope(u)
        listOf(a1, s1, s2, x1).forEach { assertTrue(p.canSee(it), it.name) }
        assertFalse(p.canSee(a))   // the building: name only
        assertTrue(p.isVisible(a))
        listOf(b, s3, s4).forEach { assertFalse(p.isVisible(it), it.name) }
    }

    @Test
    fun `accesses add up and a change applies at once`() {
        val u = user(14, UserType.MANAGER)
        service.define(u, true, listOf(s1, b))
        val p = service.scope(u)
        assertTrue(p.canSee(x1))
        assertTrue(p.canSee(s3))
        assertFalse(p.canSee(s2))

        service.define(u, true, listOf(s2))
        val after = service.scope(u)
        assertFalse(after.canSee(s1))
        assertFalse(after.canSee(s3))
        assertTrue(after.canSee(s2))

        service.define(u, false, emptyList())
        assertTrue(service.scope(u).all)
    }

    @Test
    fun `unknown element refused, temperatures filtered`() {
        val u = user(15, UserType.VIEWER)
        assertThrows(IllegalArgumentException::class.java) { service.define(u, true, listOf(Room("?").withId(999))) }

        service.define(u, true, listOf(s1))
        val visibleIds: List<Temperature> = service.filterTemperatures(service.scope(u), listOf(m1, m3))
        assertEquals(listOf(m1), visibleIds)
    }

    @Test
    fun `groups add their accesses to the user's own`() {
        val u = user(30, UserType.VIEWER)
        service.define(u, true, listOf(s1))
        groupElements[30L] = listOf(b)
        val p = service.scope(u)
        assertTrue(p.canSee(s1))
        assertTrue(p.canSee(s3))       // through the group's building B
        assertFalse(p.canSee(s2))

        // A user who sees everything is not limited by a group
        val all = user(31, UserType.VIEWER)
        groupElements[31L] = listOf(s4)
        assertTrue(service.scope(all).all)
    }
}
