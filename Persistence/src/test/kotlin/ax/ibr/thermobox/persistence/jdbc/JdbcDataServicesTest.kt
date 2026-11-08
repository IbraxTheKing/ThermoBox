package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.thermobox.persistence.migration.DatabaseMigrations
import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertDevice
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The JDBC data services against a real database (MySQL or MariaDB).
 *
 * Skipped unless `THERMOBOX_TEST_DB_URL`, `THERMOBOX_TEST_DB_USER` and `THERMOBOX_TEST_DB_PASSWORD`
 * name a database for the tests: **it is emptied before each test**. The Flyway migrations are
 * applied first, unless `THERMOBOX_DB_MIGRATE=false`.
 */
class JdbcDataServicesTest {

    private lateinit var db: JdbcDatabase
    private lateinit var users: UserDataServiceJdbcImpl
    private lateinit var rooms: RoomDataServiceJdbcImpl
    private lateinit var links: RoomRoomAttrDataServiceJdbcImpl
    private lateinit var temperatures: TemperatureDataServiceJdbcImpl
    private lateinit var roomTemperatures: RoomTempAttrDataServiceJdbcImpl
    private lateinit var favorites: FavoriteDataServiceJdbcImpl
    private lateinit var images: RoomImageDataServiceJdbcImpl
    private lateinit var accesses: AccessDataServiceJdbcImpl
    private lateinit var groups: UserGroupDataServiceJdbcImpl

    @BeforeEach
    fun empty() {
        db = database()
        users = UserDataServiceJdbcImpl(db)
        rooms = RoomDataServiceJdbcImpl(db)
        links = RoomRoomAttrDataServiceJdbcImpl(db)
        temperatures = TemperatureDataServiceJdbcImpl(db)
        roomTemperatures = RoomTempAttrDataServiceJdbcImpl(db)
        favorites = FavoriteDataServiceJdbcImpl(db)
        images = RoomImageDataServiceJdbcImpl(db)
        accesses = AccessDataServiceJdbcImpl(db)
        groups = UserGroupDataServiceJdbcImpl(db)
        db.transaction { cx ->
            listOf(
                "audit_log", "alert_delivery", "alert_event", "alert_rule_channel", "alert_rule_scope", "alert_rule", "alert_channel",
                "measurement_hourly", "latest_measurement", "user_group_access", "user_group_member", "user_group", "restricted_access", "user_access",
                "room_image", "favorite", "room_temperature_attr", "temperature", "room_attr", "room", "`user`",
            ).forEach { cx.execute("DELETE FROM $it") }
        }
    }

    @Test
    fun `the log of the changes is written, filtered, counted, paged and purged`() {
        val log = AuditDataServiceJdbcImpl(db)
        val at = LocalDateTime.of(2026, 11, 1, 10, 0)
        log.add(AuditEntry(at = at, userId = 1, username = "admin", role = "ADMIN", action = "setpoint.set", targetType = "element", targetId = 12, targetName = "Salle 101", details = "{\"changes\":{\"value\":22}}"))
        log.add(AuditEntry(at = at.plusDays(1), userId = 2, username = "gestion", role = "MANAGER", action = "rule.create", targetType = "rule", targetId = 3, targetName = "Tout_le site"))
        log.add(AuditEntry(at = at.plusDays(2), userId = 2, username = "gestion2", role = "MANAGER", action = "rule.update", targetType = "rule", targetId = 3, targetName = "Tout le site"))
        log.add(AuditEntry(at = at.minusDays(90), userId = null, username = "ghost", role = null, action = "style.set"))

        assertEquals(listOf("rule.update", "rule.create"), log.find(AuditQuery(action = "rule")).map { it.action })
        assertEquals(1L, log.count(AuditQuery(action = "rule.create")))
        assertEquals(2L, log.count(AuditQuery(userId = 2)))
        // _ is a character, not « any »
        assertEquals(listOf("Tout_le site"), log.find(AuditQuery(text = "t_le")).map { it.targetName })
        assertEquals(2L, log.count(AuditQuery(from = at, to = at.plusDays(2))))
        assertEquals(listOf("rule.create"), log.find(AuditQuery(offset = 1, limit = 1)).map { it.action })
        assertEquals("{\"changes\":{\"value\":22}}", log.find(AuditQuery(targetType = "element", targetId = 12)).single().details)
        assertEquals(listOf(1L to "admin", 2L to "gestion2"), log.users())
        assertEquals(1, log.purge(at.minusDays(30)))
        assertEquals(3L, log.count(AuditQuery()))
    }

    @Test
    fun `users are added with their id, updated, found and removed`() {
        val u = User("alice", "hash", UserType.MANAGER)
        users.add(u)
        assertNotNull(u.id)
        users.add(User("bob", "hash2"))

        val read = users.getById(u.id!!)!!
        assertEquals("alice", read.username)
        assertEquals(UserType.MANAGER, read.type)

        read.type = UserType.ADMIN
        users.update(read)
        assertEquals(UserType.ADMIN, users.getByUsername("alice")!!.type)
        assertEquals(listOf("bob"), users.getByType(UserType.VIEWER).map { it.username })
        assertEquals(2, users.getAll().size)

        users.remove(read)
        assertNull(users.getById(u.id!!))
        assertNull(users.getByUsername("alice"))
    }

    @Test
    fun `who the person is and the provisional state are kept, the address is found`() {
        val expires = LocalDateTime.of(2026, 11, 9, 8, 30)
        val u = User("jdupont", "hash", UserType.VIEWER).apply {
            firstName = "Jean"
            lastName = "Dupont"
            email = "Jean.Dupont@exemple.fr"
            passwordChangeRequired = true
            provisionalExpiresAt = expires
        }
        users.add(u)
        users.add(User("bob", "hash2"))

        val read = users.getByEmail("jean.dupont@EXEMPLE.fr")!!
        assertEquals(u.id, read.id)
        assertEquals("Jean", read.firstName)
        assertEquals("Dupont", read.lastName)
        assertTrue(read.passwordChangeRequired)
        assertEquals(expires, read.provisionalExpiresAt)
        assertFalse(users.getByUsername("bob")!!.passwordChangeRequired)
        assertNull(users.getByEmail("personne@exemple.fr"))

        read.passwordChangeRequired = false
        read.provisionalExpiresAt = null
        users.update(read)
        assertFalse(users.getById(u.id!!)!!.passwordChangeRequired)
        assertNull(users.getById(u.id!!)!!.provisionalExpiresAt)
    }

    @Test
    fun `rooms keep their kind, links give parents and children`() {
        val building = Building("B").also(rooms::add)
        val floor = Floor("F1").also(rooms::add)
        val room = Room("R101").also(rooms::add)
        val device = Device("D1").also(rooms::add)

        assertInstanceOf(Building::class.java, rooms.getById(building.id!!))
        assertInstanceOf(Device::class.java, rooms.getByName("D1"))
        // Exactly the kind asked: a Room is not a Building
        assertEquals(listOf("R101"), rooms.getAllByType(Room()).map { it.name })
        assertEquals(listOf("F1"), rooms.getAllByType(Floor(null)).map { it.name })

        links.add(RoomRoomAttr(building, floor))
        links.add(RoomRoomAttr(floor, room))
        links.add(RoomRoomAttr(room, device))

        assertEquals(listOf("F1"), links.getChildren(building).map { it.name })
        assertEquals("F1", links.getParent(room)?.name)
        assertInstanceOf(Floor::class.java, links.getParent(room))
        assertNull(links.getParent(building))
        assertEquals(2, links.getByRoom(floor).size)

        room.name = "R102"
        rooms.update(room)
        assertEquals("R102", links.getChildren(floor).single().name)
    }

    @Test
    fun `temperatures of a device, saved and deleted with their link`() {
        val device = Device("D1").also(rooms::add)
        val now = LocalDateTime.now().withNano(0)
        val m1 = RoomTempAttr(device, Measurement(20f, now.minusHours(2)))
        val m2 = RoomTempAttr(device, Measurement(22f, now.minusHours(1)))
        val s = RoomTempAttr(device, Setpoint(19f, now.minusMinutes(30)))
        listOf(m1, m2, s).forEach(roomTemperatures::add)
        assertNotNull(m1.temperature.id)

        assertEquals(listOf(19f, 22f, 20f), roomTemperatures.getByRoom(device)!!.map { it.temperature.value })
        assertEquals(22f, roomTemperatures.getCurrentTemperatureFromRoom(device, Measurement(0f, now))?.value)
        assertInstanceOf(Setpoint::class.java, roomTemperatures.getSetpointFromRoom(device))
        assertEquals(setOf(22f, 19f), roomTemperatures.getCurrentTemperaturesFromRoom(device)!!.map { it.value }.toSet())
        assertEquals(21f, roomTemperatures.getAverageTemperatureFromRoom(device, Measurement(0f, now))?.value)
        assertEquals(device.id, roomTemperatures.getByTemperature(m2.temperature)?.room?.id)

        val zone = ZoneId.systemDefault()
        val start = now.minusHours(3).atZone(zone).toInstant()
        val end = now.plusMinutes(1).atZone(zone).toInstant()
        assertEquals(listOf(20f, 22f), roomTemperatures.getTemperaturesFromRoomTimed(device, start, end).map { it.value })
        assertEquals(listOf(19f), roomTemperatures.getSetpointsFromRoomTimed(device, start, end).map { it.value })
        assertEquals(3, roomTemperatures.getAllByTime(start, end).size)
        assertTrue(roomTemperatures.getAverageTemperatureFromRoomTimed(device, start, end).isNotEmpty())

        assertEquals(listOf(22f, 20f), temperatures.getByType(Measurement(0f, now)).map { it.value })
        assertEquals(21f, temperatures.getAverage(Measurement(0f, now))?.value)

        // The link goes with its temperature
        roomTemperatures.remove(m1)
        assertNull(temperatures.getById(m1.temperature.id!!))
        assertEquals(2, roomTemperatures.getByRoom(device)!!.size)
        assertNull(roomTemperatures.getByRoom(Device("unsaved")))
    }

    @Test
    fun `the latest measurement of each device follows the writes`() {
        val d1 = Device("D1").also(rooms::add)
        val d2 = Device("D2").also(rooms::add)
        val d3 = Device("D3").also(rooms::add)
        val now = LocalDateTime.now().withNano(0)
        val old = RoomTempAttr(d1, Measurement(20f, now.minusHours(2))).also(roomTemperatures::add)
        val recent = RoomTempAttr(d1, Measurement(21f, now.minusHours(1))).also(roomTemperatures::add)
        // Added later but older: not the latest
        RoomTempAttr(d1, Measurement(30f, now.minusHours(5))).also(roomTemperatures::add)
        RoomTempAttr(d2, Measurement(18f, now)).also(roomTemperatures::add)
        // A setpoint is not a measurement
        RoomTempAttr(d2, Setpoint(25f, now.plusMinutes(1))).also(roomTemperatures::add)

        val latest = roomTemperatures.getLatestMeasurements(listOf(d1, d2, d3))
        assertEquals(mapOf(d1.id to 21f, d2.id to 18f), latest.mapValues { it.value.value })
        assertEquals(21f, roomTemperatures.getCurrentTemperatureFromRoom(d1, Measurement(0f, now))?.value)

        // The latest deleted: the previous one takes its place
        roomTemperatures.remove(recent)
        assertEquals(20f, roomTemperatures.getCurrentTemperatureFromRoom(d1, Measurement(0f, now))?.value)

        // Its date changed: worked out again
        val t = old.temperature
        t.date = now.minusHours(10)
        temperatures.update(t)
        assertEquals(30f, roomTemperatures.getCurrentTemperatureFromRoom(d1, Measurement(0f, now))?.value)

        // A row missing (database filled by hand) is worked out on the first read
        db.transaction { it.execute("DELETE FROM latest_measurement") }
        assertEquals(30f, roomTemperatures.getLatestMeasurements(listOf(d1))[d1.id]?.value)
        assertEquals(1, db.read { cx -> cx.query("SELECT COUNT(*) AS n FROM latest_measurement") { it.getInt("n") }.first() })

        // Deleting the device's history and the device
        roomTemperatures.getByRoom(d2).orEmpty().forEach(roomTemperatures::remove)
        rooms.remove(d2)
        assertTrue(roomTemperatures.getLatestMeasurements(listOf(d1, d2)).keys == setOf(d1.id))
    }

    @Test
    fun `periods are read through the copies of kind and date, kept up to date`() {
        val d1 = Device("D1").also(rooms::add)
        val d2 = Device("D2").also(rooms::add)
        val now = LocalDateTime.now().withNano(0)
        val zone = ZoneId.systemDefault()
        fun at(t: LocalDateTime) = t.atZone(zone).toInstant()
        val inside = RoomTempAttr(d1, Measurement(20f, now.minusHours(3))).also(roomTemperatures::add)
        roomTemperatures.add(RoomTempAttr(d1, Measurement(21f, now.minusHours(1))))
        roomTemperatures.add(RoomTempAttr(d1, Measurement(25f, now.minusDays(3))))
        roomTemperatures.add(RoomTempAttr(d2, Measurement(18f, now.minusHours(2))))
        roomTemperatures.add(RoomTempAttr(d2, Setpoint(19f, now.minusHours(2))))

        val copies = db.read { cx -> cx.query("SELECT kind, date FROM room_temperature_attr WHERE id = ?", inside.id) { it.getString("kind") to it.dateTime("date") }.single() }
        assertEquals("Measurement" to now.minusHours(3), copies)

        val day = roomTemperatures.getMeasurementsOf(listOf(d1, d2, Device("unsaved")), at(now.minusDays(1)), at(now))
        assertEquals(mapOf(d1.id to listOf(20f, 21f), d2.id to listOf(18f)), day.mapValues { e -> e.value.map { it.value } })

        // A date changed on the temperature itself: the copy follows
        val t = inside.temperature
        t.date = now.minusDays(2)
        temperatures.update(t)
        assertEquals(listOf(21f), roomTemperatures.getTemperaturesFromRoomTimed(d1, at(now.minusDays(1)), at(now)).map { it.value })
        assertEquals(listOf(19f), roomTemperatures.getSetpointsFromRoomTimed(d2, at(now.minusDays(1)), at(now)).map { it.value })
        assertEquals(setOf(18f, 19f), roomTemperatures.getCurrentTemperaturesFromRoom(d2)!!.map { it.value }.toSet())
    }

    @Test
    fun `hourly averages follow every write`() {
        val d = Device("D").also(rooms::add)
        val d2 = Device("D2").also(rooms::add)
        val hour = LocalDateTime.now().withNano(0).withSecond(0).withMinute(0).minusHours(5)
        val zone = ZoneId.systemDefault()
        fun at(t: LocalDateTime) = t.atZone(zone).toInstant()
        fun hourly() = roomTemperatures.getHourlyMeasurementsOf(listOf(d, d2), at(hour.minusHours(1)), at(hour.plusHours(3)))
            .mapValues { e -> e.value.map { it.date!!.hour to it.value } }

        val a = RoomTempAttr(d, Measurement(20f, hour.plusMinutes(10))).also(roomTemperatures::add)
        roomTemperatures.add(RoomTempAttr(d, Measurement(22f, hour.plusMinutes(40))))
        roomTemperatures.add(RoomTempAttr(d, Measurement(30f, hour.plusHours(1).plusMinutes(5))))
        roomTemperatures.add(RoomTempAttr(d, Setpoint(50f, hour.plusMinutes(20)))) // not a measurement
        assertEquals(mapOf(d.id to listOf(hour.hour to 21f, hour.plusHours(1).hour to 30f), d2.id to emptyList()), hourly())

        // Value and hour changed: both hours worked out again
        val t = a.temperature
        t.value = 26f
        t.date = hour.plusHours(1).plusMinutes(50)
        temperatures.update(t)
        assertEquals(listOf(hour.hour to 22f, hour.plusHours(1).hour to 28f), hourly()[d.id])

        // Moved to another device, then deleted
        a.room = d2
        roomTemperatures.update(a)
        assertEquals(listOf(hour.plusHours(1).hour to 26f), hourly()[d2.id])
        assertEquals(listOf(hour.hour to 22f, hour.plusHours(1).hour to 30f), hourly()[d.id])
        roomTemperatures.remove(a)
        assertEquals(emptyList<Pair<Int, Float?>>(), hourly()[d2.id])

        // Same answer as working it out from every measurement
        assertEquals(hourly()[d.id], roomTemperatures.getMeasurementsOf(listOf(d), at(hour.minusHours(1)), at(hour.plusHours(3)))[d.id]!!
            .groupBy { it.date!!.truncatedTo(java.time.temporal.ChronoUnit.HOURS) }.toSortedMap()
            .map { (h, l) -> h.hour to l.map { it.value!! }.average().toFloat() })
    }

    @Test
    fun `setpoints, owners and pages in one query`() {
        val room = Room("R").also(rooms::add)
        val floor = Floor("F").also(rooms::add)
        val device = Device("D").also(rooms::add)
        val now = LocalDateTime.now().withNano(0)
        roomTemperatures.add(RoomTempAttr(room, Setpoint(19f, now.minusDays(1))))
        roomTemperatures.add(RoomTempAttr(room, Setpoint(21f, now)))
        roomTemperatures.add(RoomTempAttr(floor, Setpoint(18f, now)))
        val ms = (1..5).map { RoomTempAttr(device, Measurement(20f + it, now.minusMinutes(it.toLong()))).also(roomTemperatures::add) }

        assertEquals(mapOf(room.id to 21f, floor.id to 18f), roomTemperatures.getLatestSetpoints(listOf(room, floor, device)).mapValues { it.value.value })
        val owners = roomTemperatures.getRoomIdsOfTemperatures(ms.map { it.temperature.id!! } + -1L)
        assertEquals(5, owners.size)
        assertTrue(owners.values.all { it == device.id })

        // Latest first
        assertEquals(listOf(21f, 22f), temperatures.getPage(Measurement(0f, now), 0, 2).map { it.value })
        assertEquals(listOf(23f), temperatures.getPage(Measurement(0f, now), 2, 1).map { it.value })
        assertEquals(5, temperatures.count(Measurement(0f, now)))
        assertEquals(8, temperatures.count(null))
        assertEquals(3, temperatures.getPage(null, 5, 10).size)
        assertEquals(2, roomTemperatures.getPage(0, 2).size)
    }

    @Test
    fun `favorites and images`() {
        val u = User("alice", "hash").also(users::add)
        val room = Room("R101").also(rooms::add)
        favorites.add(Favorite(u, room))
        assertEquals(listOf("R101"), favorites.getByUser(u).map { it.room.name })
        assertEquals(listOf("alice"), favorites.getByRoom(room).map { it.user.username })

        val bytes = byteArrayOf(1, 2, 3, 4)
        val image = RoomImage(room, "image/png", bytes, LocalDateTime.now().withNano(0))
        images.add(image)
        assertArrayEquals(bytes, images.getByRoom(room)!!.data)
        assertEquals(image.updated, images.getDates()[room.id])

        image.data = byteArrayOf(9)
        images.update(image)
        assertArrayEquals(byteArrayOf(9), images.getById(image.id!!)!!.data)
    }

    @Test
    fun `accesses are replaced in one go`() {
        val u = User("alice", "hash").also(users::add)
        val a = Room("A").also(rooms::add)
        val b = Room("B").also(rooms::add)

        assertFalse(accesses.isRestricted(u))
        accesses.replace(u, true, listOf(a.id!!, b.id!!, a.id!!))
        assertTrue(accesses.isRestricted(u))
        assertEquals(setOf("A", "B"), accesses.getByUser(u).map { it.room.name }.toSet())

        accesses.deleteForRoom(a)
        assertEquals(listOf("B"), accesses.getByUser(u).map { it.room.name })

        accesses.replace(u, false, listOf(a.id!!))
        assertFalse(accesses.isRestricted(u))
        assertTrue(accesses.getByUser(u).isEmpty())

        // A failed replacement leaves everything as it was (unknown room: foreign key)
        accesses.replace(u, true, listOf(b.id!!))
        assertThrows(Exception::class.java) { accesses.replace(u, true, listOf(-1L)) }
        assertEquals(listOf("B"), accesses.getByUser(u).map { it.room.name })

        accesses.add(Access(u, a))
        accesses.deleteForUser(u)
        assertFalse(accesses.isRestricted(u))
        assertTrue(accesses.getByUser(u).isEmpty())
    }

    @Test
    fun `groups, their members and their elements`() {
        val alice = User("alice", "hash").also(users::add)
        val bob = User("bob", "hash").also(users::add)
        val a = Building("A").also(rooms::add)
        val b = Room("B").also(rooms::add)

        val g = UserGroup("Technicians", "Night shift").also(groups::add)
        val h = UserGroup("Visitors").also(groups::add)
        assertEquals("Night shift", groups.getByName("Technicians")?.description)

        groups.replace(g, listOf(alice.id!!), listOf(a.id!!))
        groups.addMembers(g, listOf(alice.id!!, bob.id!!))
        groups.addElements(h, listOf(b.id!!))
        groups.addMembers(h, listOf(alice.id!!))

        assertEquals(setOf("alice", "bob"), groups.getMembers(g).map { it.username }.toSet())
        assertEquals(listOf("A"), groups.getElements(g).map { it.name })
        assertInstanceOf(Building::class.java, groups.getElements(g).single())
        assertEquals(setOf("Technicians", "Visitors"), groups.getGroupsOf(alice).map { it.name }.toSet())
        assertEquals(setOf("A", "B"), groups.getElementsOfGroupsOf(alice).map { it.name }.toSet())
        assertEquals(listOf("A"), groups.getElementsOfGroupsOf(bob).map { it.name })

        groups.removeMembers(g, listOf(bob.id!!))
        groups.removeElements(h, listOf(b.id!!))
        assertEquals(listOf("alice"), groups.getMembers(g).map { it.username })
        assertTrue(groups.getElements(h).isEmpty())

        groups.forgetRoom(a)
        groups.forgetUser(alice)
        assertTrue(groups.getElements(g).isEmpty())
        assertTrue(groups.getGroupsOf(alice).isEmpty())

        groups.addMembers(g, listOf(bob.id!!))
        groups.deleteGroup(g)
        assertNull(groups.getByName("Technicians"))
        assertEquals(listOf("Visitors"), groups.getAll().map { it.name })
    }

    @Test
    fun `alert channels, rules with their scope, events and deliveries`() {
        val alerts = AlertDataServiceJdbcImpl(db)
        val building = Building("A").also { rooms.add(it) }
        val room = Room("Server room").also { rooms.add(it) }

        val mail = alerts.save(AlertChannel(name = "Ops", kind = ChannelKind.EMAIL, settings = "{\"host\":\"smtp\"}", secret = "x"))
        val hook = alerts.save(AlertChannel(name = "Discord", kind = ChannelKind.WEBHOOK, enabled = false))
        assertEquals(listOf("Discord", "Ops"), alerts.channels().map { it.name })
        assertEquals("x", alerts.channel(mail.id!!)!!.secret)
        alerts.save(mail.copy(name = "Ops team", secret = null))
        assertNull(alerts.channel(mail.id!!)!!.secret)

        val rule = alerts.save(AlertRule(name = "Servers", tolerance = 0.5f, global = false, scope = listOf(room.id!!, building.id!!), channels = listOf(mail.id!!, hook.id!!)))
        val read = alerts.rule(rule.id!!)!!
        assertEquals(listOf(building.id, room.id).sortedBy { it }, read.scope.sortedBy { it })
        assertEquals(setOf(mail.id, hook.id), read.channels.toSet())
        assertEquals(0.5f, read.tolerance)
        // A deleted channel leaves the rule; a deleted element leaves its scope
        alerts.deleteChannel(hook.id!!)
        rooms.remove(building)
        assertEquals(listOf(mail.id), alerts.rule(rule.id!!)!!.channels)
        assertEquals(listOf(room.id), alerts.rule(rule.id!!)!!.scope)

        val since = LocalDateTime.of(2026, 11, 3, 10, 0)
        val devices = listOf(AlertDevice(7, "Box\t1", 27.5f, since, true), AlertDevice(8, null, null, null, false))
        val event = alerts.save(AlertEvent(ruleId = rule.id, ruleName = "Servers", roomId = room.id!!, roomName = "Server room", place = "A",
            exceededSince = since, triggeredAt = since.plusMinutes(10), setpoint = 22f, threshold = 22.5f, temperature = 27.5f, devices = devices))
        assertEquals(devices, alerts.event(event.id!!)!!.devices)
        assertEquals(listOf(event.id), alerts.activeEvents().map { it.id })

        val d = alerts.save(AlertDelivery(eventId = event.id, channelId = mail.id, channelName = "Ops", kind = DeliveryKind.TRIGGERED))
        assertEquals(listOf(d.id), alerts.dueDeliveries(since).map { it.id })
        alerts.save(d.copy(attempts = 1, nextAttemptAt = since.plusMinutes(5), lastError = "timeout"))
        assertTrue(alerts.dueDeliveries(since).isEmpty())
        assertEquals(listOf(d.id), alerts.dueDeliveries(since.plusMinutes(5)).map { it.id })
        alerts.save(d.copy(status = DeliveryStatus.FAILED, attempts = 6))
        assertTrue(alerts.event(event.id!!)!!.deliveryFailed)
        assertEquals(listOf(d.id), alerts.deliveries(DeliveryStatus.FAILED).map { it.id })

        // Resolved, then purged with its deliveries; the rule may go, the history stays
        alerts.save(alerts.event(event.id!!)!!.copy(state = AlertState.RESOLVED, resolvedAt = since.plusHours(1)))
        alerts.deleteRule(rule.id!!)
        assertEquals(AlertState.RESOLVED, alerts.events(roomId = room.id).single().state)
        assertEquals(1, alerts.purge(since.plusDays(1)))
        assertTrue(alerts.events().isEmpty())
        assertTrue(alerts.deliveries(event.id!!).isEmpty())

        // One holder of a lock at a time
        assertEquals(1, alerts.exclusively("tb-test") { alerts.exclusively("tb-test") { 2 } ?: 1 })
    }

    @Test
    fun `connections are kept open, and one that died is replaced`() {
        val env = System.getenv()
        val small = JdbcDatabase(env["THERMOBOX_TEST_DB_URL"]!!, env["THERMOBOX_TEST_DB_USER"] ?: "root",
            env["THERMOBOX_TEST_DB_PASSWORD"] ?: "", maxOpen = 2, checkAfterMillis = 0)
        small.use { db1 ->
            fun id() = db1.read { cx -> cx.createStatement().use { st -> st.executeQuery("SELECT CONNECTION_ID()").use { it.next(); it.getLong(1) } } }
            val first = id()
            // The same connection again: given back, not closed
            assertEquals(first, id())
            // Killed by the server: replaced before being lent
            db.read { cx -> cx.createStatement().use { it.execute("KILL $first") } }
            assertNotEquals(first, id())
        }
    }

    @Test
    fun `no more connections than allowed, the others wait or give up`() {
        val env = System.getenv()
        val small = JdbcDatabase(env["THERMOBOX_TEST_DB_URL"]!!, env["THERMOBOX_TEST_DB_USER"] ?: "root",
            env["THERMOBOX_TEST_DB_PASSWORD"] ?: "", maxOpen = 1, waitSeconds = 1)
        small.use { db1 ->
            db1.read { _ ->
                // The only connection is taken: another operation gives up after 1 s
                val other = java.util.concurrent.Executors.newSingleThreadExecutor()
                val failure = runCatching { other.submit<Int> { db1.read { 1 } }.get() }.exceptionOrNull()
                other.shutdown()
                assertInstanceOf(java.sql.SQLTransientConnectionException::class.java, failure?.cause)
            }
            // Given back: available again
            assertEquals(1, db1.read { cx -> cx.query("SELECT 1 AS x") { it.getInt("x") }.first() })
        }
    }

    @Test
    fun `nulls and kinds round-trip`() {
        val t = Temperature()
        temperatures.add(t)
        val read = temperatures.getById(t.id!!)!!
        assertEquals(Temperature::class.java, read.javaClass)
        assertNull(read.value)
        assertNull(read.date)

        val u = User().also(users::add)
        assertNull(users.getById(u.id!!)!!.type)
    }

    companion object {
        private var shared: JdbcDatabase? = null

        private fun database(): JdbcDatabase {
            shared?.let { return it }
            val env = System.getenv()
            val url = env["THERMOBOX_TEST_DB_URL"]
            assumeTrue(url != null, "THERMOBOX_TEST_DB_URL not set: JDBC tests skipped")
            val user = env["THERMOBOX_TEST_DB_USER"] ?: "root"
            val password = env["THERMOBOX_TEST_DB_PASSWORD"] ?: ""
            if (DatabaseMigrations.enabled()) DatabaseMigrations.migrate(url!!, user, password)
            return JdbcDatabase(url!!, user, password).also { shared = it }
        }

        @JvmStatic
        @AfterAll
        fun close() {
            shared?.close()
            shared = null
        }
    }
}

/** THERMOBOX_DB_ACCESS, without a database. */
class AccessModeTest {

    @Test
    fun `jdbc by default, jpa on demand, anything else refused`() {
        // JDBC by default since 26.12; JPA when asked
        assertEquals(PersistenceFactory.AccessMode.JDBC, PersistenceFactory.accessMode(emptyMap()))
        assertEquals(PersistenceFactory.AccessMode.JDBC, PersistenceFactory.accessMode(mapOf("THERMOBOX_DB_ACCESS" to " ")))
        assertEquals(PersistenceFactory.AccessMode.JDBC, PersistenceFactory.accessMode(mapOf("THERMOBOX_DB_ACCESS" to " JDBC ")))
        assertEquals(PersistenceFactory.AccessMode.JPA, PersistenceFactory.accessMode(mapOf("THERMOBOX_DB_ACCESS" to "jpa")))
        assertThrows(IllegalStateException::class.java) { PersistenceFactory.accessMode(mapOf("THERMOBOX_DB_ACCESS" to "hibernate")) }
    }

    @Test
    fun `size of the connection pool`() {
        assertEquals(20, PersistenceFactory.poolSize(emptyMap()))
        assertEquals(5, PersistenceFactory.poolSize(mapOf("THERMOBOX_DB_POOL_SIZE" to " 5 ")))
        assertThrows(IllegalStateException::class.java) { PersistenceFactory.poolSize(mapOf("THERMOBOX_DB_POOL_SIZE" to "0")) }
        assertThrows(IllegalStateException::class.java) { PersistenceFactory.poolSize(mapOf("THERMOBOX_DB_POOL_SIZE" to "many")) }
    }

    @Test
    fun `driver of the URL`() {
        assertEquals("com.mysql.cj.jdbc.Driver", JdbcDatabase.driverClass("jdbc:mysql://localhost:3306/thermobox"))
        assertEquals("org.mariadb.jdbc.Driver", JdbcDatabase.driverClass("jdbc:mariadb://localhost/thermobox"))
        assertNull(JdbcDatabase.driverClass("jdbc:h2:mem:x"))
    }
}
