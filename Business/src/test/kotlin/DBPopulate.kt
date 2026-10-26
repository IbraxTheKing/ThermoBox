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
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Inserts a demonstration data set through the business services:
 * 3 buildings (one of them an annex without floors), their floors and rooms, several devices per
 * room, 2 unattached devices, one user per role, and [HISTORY_DAYS] days of measurements with a
 * setpoint change halfway.
 *
 * Can be run again: a building already there (same name) is skipped, like existing users.
 * Run `main` with the same environment variables as the application (THERMOBOX_DB_*).
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

    private val rooms = BusinessFactory.getRoomService()
    private val hierarchy = BusinessFactory.getHierarchyService()
    private val roomTemps = BusinessFactory.getRoomTempAttrService()
    private val users = BusinessFactory.getUserService()

    // Fixed seed: the same data on every run.
    private val random = Random(42)

    @JvmStatic
    fun main(args: Array<String>) {
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

    private fun <T : Room> create(room: T, parent: Room?): T {
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
