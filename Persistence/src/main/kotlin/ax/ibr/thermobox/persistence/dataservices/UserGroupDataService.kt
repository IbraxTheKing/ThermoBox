package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

/** Rows of "user_group", "user_group_member" and "user_group_access". */
interface UserGroupDataService : CrudService<UserGroup> {

    fun getByName(name: String): UserGroup?

    fun getMembers(group: UserGroup): List<User>

    fun getElements(group: UserGroup): List<Room>

    /** Groups [user] belongs to. */
    fun getGroupsOf(user: User): List<UserGroup>

    /** Elements of every group [user] belongs to. */
    fun getElementsOfGroupsOf(user: User): List<Room>

    /** Replaces, in one transaction, the members and the elements of [group]. */
    fun replace(group: UserGroup, members: Collection<Long>, elements: Collection<Long>)

    fun addMembers(group: UserGroup, members: Collection<Long>)

    fun removeMembers(group: UserGroup, members: Collection<Long>)

    fun addElements(group: UserGroup, elements: Collection<Long>)

    fun removeElements(group: UserGroup, elements: Collection<Long>)

    /** Deletes the group with its members and elements. */
    fun deleteGroup(group: UserGroup)

    fun forgetUser(user: User)

    fun forgetRoom(room: Room)
}
