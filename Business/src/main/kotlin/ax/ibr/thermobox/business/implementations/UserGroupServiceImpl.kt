package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.UserGroupDetail
import ax.ibr.thermobox.common.services.UserGroupService
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

/** See [UserGroupService]. */
class UserGroupServiceImpl(
    private val groups: UserGroupDataService = PersistenceFactory.getUserGroupDataService(),
    private val rooms: RoomService = BusinessFactory.getRoomService(),
) : UserGroupService {

    override fun getAll(): List<UserGroup> = groups.getAll().sortedBy { it.name.lowercase() }

    override fun getById(id: Long): UserGroup? = groups.getById(id)

    override fun detail(group: UserGroup): UserGroupDetail =
        UserGroupDetail(group, groups.getMembers(group).sortedBy { it.username?.lowercase() }, groups.getElements(group))

    override fun getGroupsOf(user: User): List<UserGroup> = groups.getGroupsOf(user).sortedBy { it.name.lowercase() }

    override fun save(group: UserGroup?, name: String, description: String?, members: Collection<User>, elements: Collection<Room>): UserGroup {
        val n = name.trim()
        require(n.isNotEmpty()) { "The group name is required" }
        require(n.length <= 100) { "Name too long (100 characters at most)" }
        groups.getByName(n)?.let { other -> require(other.id == group?.id) { "A group is already named \"$n\"" } }
        val ids = checkElements(elements)
        val g = group?.id?.let { groups.getById(it) } ?: UserGroup(n)
        g.name = n
        g.description = description?.trim()?.takeIf { it.isNotEmpty() }
        if (g.id == null) groups.add(g) else groups.update(g)
        val saved = g.id?.let { groups.getById(it) } ?: groups.getByName(n) ?: error("Group not saved")
        groups.replace(saved, members.mapNotNull { it.id }, ids)
        return saved
    }

    override fun addMembers(group: UserGroup, members: Collection<User>) = groups.addMembers(group, members.mapNotNull { it.id })

    override fun removeMembers(group: UserGroup, members: Collection<User>) = groups.removeMembers(group, members.mapNotNull { it.id })

    override fun addElements(group: UserGroup, elements: Collection<Room>) = groups.addElements(group, checkElements(elements))

    override fun removeElements(group: UserGroup, elements: Collection<Room>) = groups.removeElements(group, elements.mapNotNull { it.id })

    override fun deleteGroup(group: UserGroup) = groups.deleteGroup(group)

    // Accesses are given on buildings, floors and rooms; a device follows its room
    private fun checkElements(elements: Collection<Room>): List<Long> = elements.mapNotNull { it.id }.distinct().onEach { id ->
        val s = requireNotNull(rooms.getById(id)) { "Element $id not found" }
        require(s !is Device) { "\"${s.name}\" is a device: give access to its room" }
    }
}
