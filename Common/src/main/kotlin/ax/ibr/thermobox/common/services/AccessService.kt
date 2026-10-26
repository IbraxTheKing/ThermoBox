package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.access.AccessScope
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User

/**
 * Which buildings, floors and rooms each user may see.
 *
 * Rules:
 * - an ADMIN sees and manages everything, whatever is recorded;
 * - a user is either unlimited (sees everything, the case of the accounts created before this
 *   feature) or limited to their accesses;
 * - an access to an element covers everything it contains, now and later (a room added to an
 *   allowed floor is allowed too); accesses only add up, there is no "deny", so no conflict;
 * - nothing is cached between two requests: a change applies to the next request.
 */
interface AccessService {

    /** true if [user] only sees their accesses (never for an ADMIN). */
    fun isRestricted(user: User): Boolean

    /** Elements granted to [user] directly (buildings, floors, rooms). */
    fun getGrants(user: User): List<Room>

    /** Elements granted to [user] through their groups (UserGroupService). */
    fun getGroupGrants(user: User): List<Room> = emptyList()

    /**
     * Replaces the accesses of [user]: unlimited, or limited to [rooms] and their content.
     * @throws IllegalArgumentException for an ADMIN (always unlimited)
     */
    fun define(user: User, restricted: Boolean, rooms: Collection<Room>)

    /** Access of a user created now: limited, with no element, until an ADMIN grants some. */
    fun initialize(user: User)

    /** What [user] may see right now (nothing for null). */
    fun scope(user: User?): AccessScope

    /** [temperatures] restricted to those of the rooms / devices [scope] allows. */
    fun filterTemperatures(scope: AccessScope, temperatures: List<Temperature>): List<Temperature>

    /** Forgets the accesses to [room] (before deleting it). */
    fun forgetRoom(room: Room)

    /** Forgets the accesses of [user] (before deleting them). */
    fun forgetUser(user: User)
}
