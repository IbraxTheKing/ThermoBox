package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.access.AccessScope
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.AccessService
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.persistence.dataservices.AccessDataService
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

/** See [AccessService] for the rules. */
class AccessServiceImpl(
    private val access: AccessDataService = PersistenceFactory.getAccessDataService(),
    private val hierarchy: HierarchyService = BusinessFactory.getHierarchyService(),
    private val rooms: RoomService = BusinessFactory.getRoomService(),
    /** Groups of users; read only when needed (tests pass their own). */
    private val groups: () -> UserGroupDataService = { PersistenceFactory.getUserGroupDataService() },
    private val roomTemps: () -> RoomTempAttrService = { BusinessFactory.getRoomTempAttrService() },
) : AccessService {

    override fun isRestricted(user: User): Boolean = user.type != UserType.ADMIN && access.isRestricted(user)

    override fun getGrants(user: User): List<Room> = access.getByUser(user).map { it.room }

    override fun getGroupGrants(user: User): List<Room> = groups().getElementsOfGroupsOf(user)

    override fun define(user: User, restricted: Boolean, rooms: Collection<Room>) {
        require(user.type != UserType.ADMIN || !restricted) { "An administrator always has access to everything" }
        val ids = rooms.mapNotNull { it.id }
        ids.forEach { requireNotNull(this.rooms.getById(it)) { "Element $it not found" } }
        access.replace(user, restricted, if (restricted) ids else emptyList())
    }

    override fun initialize(user: User) {
        if (user.id == null || user.type == UserType.ADMIN) return
        access.replace(user, true, emptyList())
    }

    override fun scope(user: User?): AccessScope {
        if (user == null) return AccessScope.NONE
        if (!isRestricted(user)) return AccessScope.ALL
        val accessibleIds = HashSet<Long>()
        val visibleIds = HashSet<Long>()
        // Own accesses and those of the user's groups add up
        for (room in getGrants(user) + getGroupGrants(user)) {
            descend(room, accessibleIds)
            hierarchy.getPath(room).mapNotNullTo(visibleIds) { it.id }
        }
        return AccessScope(false, accessibleIds, visibleIds)
    }

    // The element and everything below it. Guards against a cycle in corrupted data.
    private fun descend(room: Room, ids: MutableSet<Long>) {
        val id = room.id ?: return
        if (!ids.add(id)) return
        hierarchy.getChildren(room).forEach { descend(it, ids) }
    }

    override fun filterTemperatures(scope: AccessScope, temperatures: List<Temperature>): List<Temperature> {
        if (scope.all) return temperatures
        // Temperature → the room or device it belongs to, for these temperatures only
        val rooms = roomTemps().getRoomIdsOfTemperatures(temperatures.mapNotNull { it.id })
        return temperatures.filter { t -> t.id?.let { rooms[it] }?.let { scope.canSee(it) } == true }
    }

    override fun forgetRoom(room: Room) {
        access.deleteForRoom(room)
        groups().forgetRoom(room)
    }

    override fun forgetUser(user: User) {
        access.deleteForUser(user)
        groups().forgetUser(user)
    }
}
