package ax.ibr.thermobox.business.hierarchy

import ax.ibr.thermobox.business.implementations.HierarchyServiceImpl
import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.common.services.RoomService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HierarchyCacheTest {

    private fun <T : Room> T.withId(id: Long) = apply { this.id = id }

    private val b = Building("B").withId(1)
    private val f = Floor("F").withId(2)
    private val r = Room("R").withId(3)
    private val d = Device("D").withId(4)
    private val elements = mutableListOf<Room>(b, f, r, d)
    private val links = mutableListOf(RoomRoomAttr(b, f), RoomRoomAttr(f, r), RoomRoomAttr(r, d))
    private var reads = 0

    private val roomService = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RoomService::class.java)) { _, m, _ ->
        if (m.name == "getAll") { reads++; elements.toList() } else throw UnsupportedOperationException(m.name)
    } as RoomService
    private val linkService = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RoomRoomAttrService::class.java)) { _, m, _ ->
        if (m.name == "getAll") links.toList() else throw UnsupportedOperationException(m.name)
    } as RoomRoomAttrService

    private var now = Instant.parse("2026-10-27T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val cache = HierarchyCache(roomService, linkService, Duration.ofSeconds(30), clock)
    private val hierarchy = HierarchyServiceImpl(roomService, linkService, cache)

    @Test
    fun `the hierarchy is read once, in memory afterwards`() {
        assertEquals(listOf(b.id, f.id), hierarchy.getPath(r).map { it.id })
        assertEquals(listOf(d.id), hierarchy.getDevices(b).map { it.id })
        assertEquals(listOf(f.id), hierarchy.getChildren(b).map { it.id })
        assertInstanceOf(Floor::class.java, hierarchy.getParent(r))
        assertEquals(listOf(b.id), hierarchy.getRoots().map { it.id })
        assertEquals(1, reads)
    }

    @Test
    fun `copies are handed out, a change through the services reloads, the others after the delay`() {
        val parent = hierarchy.getParent(r)!!
        assertNotSame(f, parent)
        parent.name = "changed by a caller"
        assertEquals("F", hierarchy.getParent(r)!!.name)

        // Changed elsewhere (another server): seen after the delay
        links.removeIf { it.roomB.id == d.id }
        assertEquals(listOf(d.id), hierarchy.getDevices(r).map { it.id })
        now = now.plusSeconds(31)
        assertEquals(emptyList<Long?>(), hierarchy.getDevices(r).map { it.id })

        // Changed through the services: seen at once
        links += RoomRoomAttr(r, d)
        HierarchyCache.invalidate()
        assertEquals(listOf(d.id), hierarchy.getDevices(r).map { it.id })
        assertNull(hierarchy.getParent(b))
    }
}
