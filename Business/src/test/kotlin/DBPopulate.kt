import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.io.simulation.SimulatedClimate
import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.persistence.LatestMeasurements
import ax.ibr.thermobox.persistence.jdbc.JdbcDatabase
import java.sql.Statement
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Fills the database, through the business services, with one of two data sets:
 *
 * - `demo` (default): 3 buildings (one of them an annex without floors), their floors and rooms,
 *   several devices per room, 2 unattached devices, one user per role, and [HISTORY_DAYS] days of
 *   measurements with a setpoint change halfway. Small and readable, for a demonstration.
 * - `huge`: a large site to see how everything behaves on a big database (see [HugeOptions]):
 *   by default 10 buildings x 4 floors x 8 rooms x 3 devices (960 devices), 30 days of measurements
 *   every 10 minutes (about 4 million), 500 users with limited accesses and 20 groups. The
 *   measurements are written with JDBC batches, the rest through the business services.
 *
 * Usage: `main demo`, `main huge`, or `main huge buildings=20 days=90 step=5` to change a size
 * (THERMOBOX_SEED=huge works too). Can be run again: a building already there (same name) is
 * skipped, like existing users. Run `main` with the same environment variables as the application
 * (THERMOBOX_DB_*).
 */
object DatabaseSeeder {

    private const val HISTORY_DAYS = 3L
    private const val STEP_MINUTES = 30L
    private const val PASSWORD = "password123"

    /** [code] prefixes the device names, which must be unique (the drivers recognize them by name). */
    private class RoomSeed(val name: String, val code: String, val setpoint: Float, val devices: Int)
    private class FloorSeed(val name: String, val rooms: List<RoomSeed>)
    private class BuildingSeed(val name: String, val floors: List<FloorSeed>, val rooms: List<RoomSeed> = emptyList())

    private val buildings = listOf(
        BuildingSeed(
            "Building A – Headquarters", listOf(
                FloorSeed("A · Ground floor", listOf(
                    RoomSeed("Reception", "A-REC", 21f, 2),
                    RoomSeed("Cafeteria", "A-CAF", 20.5f, 2),
                )),
                FloorSeed("A · Floor 1", listOf(
                    RoomSeed("Open space 1", "A-OS1", 21f, 3),
                    RoomSeed("Lake meeting room", "A-LAK", 21f, 1),
                    RoomSeed("Bureau de direction", "A-DIR", 21.5f, 1),
                )),
                FloorSeed("A · Floor 2", listOf(
                    RoomSeed("Open space 2", "A-OS2", 21f, 3),
                    RoomSeed("Server room", "A-SRV", 18f, 2),
                )),
            )
        ),
        BuildingSeed(
            "Building B – Workshop", listOf(
                FloorSeed("B · Ground floor", listOf(
                    RoomSeed("Atelier", "B-ATL", 18f, 3),
                    RoomSeed("Storage", "B-STO", 16f, 2),
                )),
                FloorSeed("B · Floor 1", listOf(
                    RoomSeed("Laboratory", "B-LAB", 20f, 2),
                    RoomSeed("Break room", "B-PAU", 21f, 1),
                )),
            )
        ),
        // No floors: the rooms are directly in the building.
        BuildingSeed(
            "Annex C", emptyList(), listOf(
                RoomSeed("Multipurpose room", "C-POL", 19.5f, 2),
                RoomSeed("Locker rooms", "C-LOC", 20f, 1),
            )
        ),
    )

    /** Devices "just plugged in", to be put in a room by drag and drop. */
    private val unattachedDevices = listOf("New device 1", "New device 2")

    private val seedUsers = listOf(
        "admin" to UserType.ADMIN,
        "manager" to UserType.MANAGER,
        "viewer" to UserType.VIEWER,
    )

    internal val rooms = BusinessFactory.getRoomService()
    private val hierarchy = BusinessFactory.getHierarchyService()
    internal val roomTemps = BusinessFactory.getRoomTempAttrService()
    internal val users = BusinessFactory.getUserService()

    // Fixed seed: the same data on every run.
    private val random = Random(42)

    @JvmStatic
    fun main(args: Array<String>) {
        val profile = args.firstOrNull { '=' !in it } ?: System.getenv("THERMOBOX_SEED") ?: "demo"
        when (profile.trim().lowercase()) {
            "demo" -> seedDemo()
            "huge" -> HugeSeeder(HugeOptions.parse(args.filter { '=' in it })).run()
            else -> error("Unknown data set \"$profile\": demo or huge")
        }
    }

    private fun seedDemo() {
        val end = LocalDateTime.now().withSecond(0).withNano(0)
        val start = end.minusDays(HISTORY_DAYS)

        println("== Seed: users ==")
        seedUsers.forEach { (username, type) ->
            if (users.getByUsername(username) == null) {
                val user = User(username, PASSWORD, type)
                users.add(user)
                // A new account sees nothing until it is given accesses: the demo accounts see everything.
                if (type != UserType.ADMIN) BusinessFactory.getAccessService().define(user, false, emptyList())
                println("User added: $username ($type) / $PASSWORD")
            } else {
                println("User already exists: $username")
            }
        }

        println("== Seed: buildings, floors, rooms, devices ==")
        val existing = rooms.getAllByType(Building(null)).mapNotNull { it.name }.toSet()
        buildings.forEach { b ->
            if (b.name in existing) {
                println("Building already exists, skipped: ${b.name}")
                return@forEach
            }
            val building = create(Building(b.name), parent = null)
            b.floors.forEach { e ->
                val floor = create(Floor(e.name), building)
                e.rooms.forEach { seedRoom(it, floor, start, end) }
            }
            b.rooms.forEach { seedRoom(it, building, start, end) }
        }

        unattachedDevices.filter { rooms.getByName(it) == null }.forEach {
            create(Device(it), parent = null)
            println("Unattached device: $it")
        }

        println("== Seed done ==")
    }

    internal fun <T : Room> create(room: T, parent: Room?): T {
        rooms.add(room)
        if (parent != null) hierarchy.setParent(room, parent)
        return room
    }

    private fun seedRoom(seed: RoomSeed, parent: Room, start: LocalDateTime, end: LocalDateTime) {
        val room = create(Room(seed.name), parent)

        // Lowered setpoint at first, normal one from the second day: the curves show the change.
        val reduced = seed.setpoint - 1.5f
        val change = start.plusDays(1)
        roomTemps.add(RoomTempAttr(room, Setpoint(reduced, start)))
        roomTemps.add(RoomTempAttr(room, Setpoint(seed.setpoint, change)))
        val setpointAt = { date: LocalDateTime -> if (date.isBefore(change)) reduced else seed.setpoint }

        for (i in 1..seed.devices) {
            val device = create(Device("${seed.code}-$i"), room)
            val name = device.name.orEmpty()
            var value = SimulatedClimate.target(setpointAt(start), name, start)
            var date = start
            while (!date.isAfter(end)) {
                value = SimulatedClimate.next(value, SimulatedClimate.target(setpointAt(date), name, date), random)
                roomTemps.add(RoomTempAttr(device, Measurement(value, date)))
                date = date.plusMinutes(STEP_MINUTES)
            }
        }
        println("Room added: ${seed.name} (${seed.devices} device(s), setpoint ${seed.setpoint} °C)")
    }
}

/**
 * Sizes of the `huge` data set, each one can be changed on the command line (`buildings=20`).
 * [step]: minutes between two measurements of a device.
 */
data class HugeOptions(
    val buildings: Int = 10,
    val floors: Int = 4,
    val rooms: Int = 8,
    val devices: Int = 3,
    val days: Int = 30,
    val step: Int = 10,
    val users: Int = 500,
    val groups: Int = 20,
    val unattached: Int = 50,
) {
    val devicesTotal: Long get() = buildings.toLong() * floors * rooms * devices
    val measurementsPerDevice: Long get() = days * 24L * 60 / step + 1
    val measurementsTotal: Long get() = devicesTotal * measurementsPerDevice

    companion object {
        fun parse(args: List<String>): HugeOptions {
            var o = HugeOptions()
            for (arg in args) {
                val (key, raw) = arg.split('=', limit = 2)
                val v = raw.trim().toIntOrNull()?.takeIf { it >= 0 } ?: error("$arg: a positive number is expected")
                o = when (key.trim().lowercase()) {
                    "buildings" -> o.copy(buildings = v)
                    "floors" -> o.copy(floors = maxOf(v, 1))
                    "rooms" -> o.copy(rooms = maxOf(v, 1))
                    "devices" -> o.copy(devices = v)
                    "days" -> o.copy(days = v)
                    "step" -> o.copy(step = maxOf(v, 1))
                    "users" -> o.copy(users = v)
                    "groups" -> o.copy(groups = v)
                    "unattached" -> o.copy(unattached = v)
                    else -> error("Unknown option $key (buildings, floors, rooms, devices, days, step, users, groups, unattached)")
                }
            }
            return o
        }
    }
}

/** The `huge` data set (see [DatabaseSeeder]). */
class HugeSeeder(private val o: HugeOptions) {

    private val hierarchy = BusinessFactory.getHierarchyService()
    private val access = BusinessFactory.getAccessService()
    private val groups = BusinessFactory.getUserGroupService()
    private val random = Random(7)

    private val end = LocalDateTime.now().withSecond(0).withNano(0)
    private val start = end.minusDays(o.days.toLong())

    /** Measurements go straight to the tables, in batches: one transaction per row would take hours. */
    private val db: JdbcDatabase by lazy {
        val url = System.getenv("THERMOBOX_DB_URL") ?: "jdbc:mysql://localhost:3306/thermobox"
        // MySQL / MariaDB send a batch as one multi-row INSERT with this option
        val fast = if ("rewriteBatchedStatements" in url) url else url + (if ('?' in url) "&" else "?") + "rewriteBatchedStatements=true"
        JdbcDatabase(fast, requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD"), maxIdle = 1)
    }

    private var written = 0L
    private val began = System.nanoTime()

    fun run() {
        println("== Huge data set: ${o.buildings} buildings x ${o.floors} floors x ${o.rooms} rooms x ${o.devices} devices " +
            "= ${o.devicesTotal} devices, ${o.days} days every ${o.step} min = ${"%,d".format(o.measurementsTotal)} measurements ==")
        val created = seedHierarchy()
        seedUnattached()
        seedUsers(created)
        db.close()
        println("== Seed done in ${elapsed()} (${"%,d".format(written)} measurements) ==")
    }

    /** The buildings created by this run (an existing one is skipped). */
    private fun seedHierarchy(): List<Building> {
        val existing = DatabaseSeeder.rooms.getAllByType(Building(null)).mapNotNull { it.name }.toSet()
        val created = ArrayList<Building>()
        for (b in 1..o.buildings) {
            val name = "Site %03d".format(b)
            if (name in existing) {
                println("Building already exists, skipped: $name")
                continue
            }
            val building = DatabaseSeeder.create(Building(name), parent = null)
            created += building
            // Setpoints at every level: the building's, and some floors and rooms with their own
            val buildingSetpoint = 19f + random.nextInt(5)
            setpoint(building, buildingSetpoint, changeHalfway = true)
            for (e in 1..o.floors) {
                val floor = DatabaseSeeder.create(Floor("$name · Floor $e"), building)
                val floorSetpoint = if (random.nextInt(4) == 0) (buildingSetpoint - 1).also { setpoint(floor, it) } else buildingSetpoint
                for (r in 1..o.rooms) {
                    val room = DatabaseSeeder.create(Room("$name · Room $e%02d".format(r)), floor)
                    val roomSetpoint = if (random.nextInt(5) == 0) (17f + random.nextInt(8)).also { setpoint(room, it) } else floorSetpoint
                    for (d in 1..o.devices) {
                        val device = DatabaseSeeder.create(Device("S%03d-%d%02d-%d".format(b, e, r, d)), room)
                        measurements(device, roomSetpoint)
                    }
                }
            }
            println("$name: ${o.floors * o.rooms} rooms, ${o.floors * o.rooms * o.devices} devices - " +
                "${"%,d".format(written)} measurements so far, ${elapsed()}")
        }
        return created
    }

    private fun setpoint(room: Room, value: Float, changeHalfway: Boolean = false) {
        if (changeHalfway) {
            DatabaseSeeder.roomTemps.add(RoomTempAttr(room, Setpoint(value - 1.5f, start)))
            DatabaseSeeder.roomTemps.add(RoomTempAttr(room, Setpoint(value, start.plus(Duration.between(start, end).dividedBy(2)))))
        } else {
            DatabaseSeeder.roomTemps.add(RoomTempAttr(room, Setpoint(value, start)))
        }
    }

    /** Every [HugeOptions.step] minutes over the period, in transactions of [CHUNK] measurements. */
    private fun measurements(device: Device, setpoint: Float) {
        val deviceId = requireNotNull(device.id)
        val name = device.name.orEmpty()
        var value = SimulatedClimate.target(setpoint, name, start)
        var date = start
        var last: Pair<Long, LocalDateTime>? = null
        while (!date.isAfter(end)) {
            val values = ArrayList<Pair<LocalDateTime, Float>>(CHUNK)
            while (values.size < CHUNK && !date.isAfter(end)) {
                value = SimulatedClimate.next(value, SimulatedClimate.target(setpoint, name, date), random)
                values += date to value
                date = date.plusMinutes(o.step.toLong())
            }
            val ids = db.transaction { cx ->
                val ids = cx.prepareStatement("INSERT INTO temperature (DTYPE, value, date) VALUES ('Measurement', ?, ?)", Statement.RETURN_GENERATED_KEYS).use { st ->
                    for ((d, v) in values) {
                        st.setFloat(1, v)
                        st.setTimestamp(2, Timestamp.valueOf(d))
                        st.addBatch()
                    }
                    st.executeBatch()
                    st.generatedKeys.use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
                }
                check(ids.size == values.size) { "Expected ${values.size} generated ids, got ${ids.size}" }
                // Kind and date copied next to the link, as the data services do (migration V6)
                cx.prepareStatement("INSERT INTO room_temperature_attr (room_id, temperature_id, kind, date) VALUES (?, ?, 'Measurement', ?)").use { st ->
                    for ((i, id) in ids.withIndex()) {
                        st.setLong(1, deviceId)
                        st.setLong(2, id)
                        st.setTimestamp(3, Timestamp.valueOf(values[i].first))
                        st.addBatch()
                    }
                    st.executeBatch()
                }
                ids
            }
            written += values.size
            last = ids.last() to values.last().first
        }
        // Its latest measurement, as the data services would have recorded it
        last?.let { (id, at) ->
            db.transaction { cx ->
                cx.prepareStatement(LatestMeasurements.UPSERT).use { st ->
                    st.setLong(1, deviceId)
                    st.setLong(2, id)
                    st.setTimestamp(3, Timestamp.valueOf(at))
                    st.executeUpdate()
                }
            }
        }
    }

    private fun seedUnattached() {
        for (i in 1..o.unattached) {
            val name = "Unattached device %03d".format(i)
            if (DatabaseSeeder.rooms.getByName(name) == null) DatabaseSeeder.create(Device(name), parent = null)
        }
        println("${o.unattached} unattached devices")
    }

    /** Users limited to a few buildings or floors, and groups giving whole buildings. */
    private fun seedUsers(buildings: List<Building>) {
        if (buildings.isEmpty()) return println("No new building: users and groups skipped")
        // BCrypt is slow on purpose: hashed once, the same password for every account
        val hash = PasswordHasher.hash(PASSWORD)
        val floorsOf = buildings.associateWith { hierarchy.getChildren(it) }
        val created = ArrayList<User>()
        for (i in 1..o.users) {
            val username = "user" + letters(i)
            if (DatabaseSeeder.users.getByUsername(username) != null) continue
            val type = if (i % 10 == 0) UserType.MANAGER else UserType.VIEWER
            val user = User(username, hash, type)
            DatabaseSeeder.users.add(user)
            val grants = (1..1 + random.nextInt(3)).map {
                val b = buildings[random.nextInt(buildings.size)]
                val floors = floorsOf.getValue(b)
                if (floors.isNotEmpty() && random.nextBoolean()) floors[random.nextInt(floors.size)] else b
            }.distinctBy { it.id }
            access.define(user, true, grants)
            created += user
        }
        println("${created.size} users (password: $PASSWORD), ${elapsed()}")

        for (g in 1..o.groups) {
            val name = "Team %02d".format(g)
            if (groups.getAll().any { it.name == name }) continue
            val members = created.shuffled(random).take(minOf(created.size, 10 + random.nextInt(30)))
            val elements = buildings.shuffled(random).take(minOf(buildings.size, 1 + random.nextInt(2)))
            groups.save(null, name, "Generated group $g", members, elements)
        }
        println("${o.groups} groups, ${elapsed()}")
    }

    /** 1 → "a", 26 → "z", 27 → "aa": usernames may only hold letters. */
    private fun letters(n: Int): String {
        var i = n
        val sb = StringBuilder()
        while (i > 0) {
            i--
            sb.append('a' + i % 26)
            i /= 26
        }
        return sb.reverse().toString()
    }

    private fun elapsed(): String {
        val s = (System.nanoTime() - began) / 1_000_000_000
        return "%d min %02d s".format(s / 60, s % 60)
    }

    private fun requireEnv(name: String) = System.getenv(name) ?: error("Environment variable $name is not set")

    private companion object {
        const val CHUNK = 2_000
        const val PASSWORD = "password123"
    }
}
