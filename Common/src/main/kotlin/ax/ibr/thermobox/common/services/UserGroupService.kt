package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User

/** A group with its members and the elements it gives access to. */
data class UserGroupDetail(val group: UserGroup, val members: List<User>, val elements: List<Room>)

/**
 * Groups of users (ADMIN). A member of a group sees the group's buildings, floors and rooms
 * (and what they contain) on top of their own accesses, when their access is limited; a user who
 * sees everything is not affected. Accesses only add up: a group never hides anything.
 */
interface UserGroupService {

    fun getAll(): List<UserGroup>

    fun getById(id: Long): UserGroup?

    fun detail(group: UserGroup): UserGroupDetail

    fun getGroupsOf(user: User): List<UserGroup>

    /**
     * Creates or updates ([group] null: creates) a group with exactly these members and elements.
     * @throws IllegalArgumentException empty or already used name, unknown element, a box
     */
    fun save(group: UserGroup?, name: String, description: String?, members: Collection<User>, elements: Collection<Room>): UserGroup

    fun addMembers(group: UserGroup, members: Collection<User>)

    fun removeMembers(group: UserGroup, members: Collection<User>)

    fun addElements(group: UserGroup, elements: Collection<Room>)

    fun removeElements(group: UserGroup, elements: Collection<Room>)

    fun deleteGroup(group: UserGroup)
}
