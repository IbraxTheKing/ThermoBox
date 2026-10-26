package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

/** Rows of "user_access" and "restricted_access". */
interface AccessDataService : CrudService<Access> {

    fun getByUser(user: User): List<Access>

    fun isRestricted(user: User): Boolean

    /** Replaces in one transaction: the restriction marker and every access of [user]. */
    fun replace(user: User, restricted: Boolean, roomIds: Collection<Long>)

    fun deleteForRoom(room: Room)

    fun deleteForUser(user: User)
}
