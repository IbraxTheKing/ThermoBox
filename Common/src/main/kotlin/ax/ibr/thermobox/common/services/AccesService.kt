package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.acces.Perimetre
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User

/**
 * Which bâtiments, étages and salles each user may see.
 *
 * Rules:
 * - an ADMIN sees and manages everything, whatever is recorded;
 * - a user is either unlimited (sees everything, the case of the accounts created before this
 *   feature) or limited to their accesses;
 * - an access to an element covers everything it contains, now and later (a salle added to an
 *   allowed étage is allowed too); accesses only add up, there is no "deny", so no conflict;
 * - nothing is cached between two requests: a change applies to the next request.
 */
interface AccesService {

    /** true if [user] only sees their accesses (never for an ADMIN). */
    fun estRestreint(user: User): Boolean

    /** Elements granted to [user] directly (bâtiments, étages, salles). */
    fun getAutorisations(user: User): List<Salle>

    /** Elements granted to [user] through their groups (GroupeService). */
    fun getAutorisationsDesGroupes(user: User): List<Salle> = emptyList()

    /**
     * Replaces the accesses of [user]: unlimited, or limited to [salles] and their content.
     * @throws IllegalArgumentException for an ADMIN (always unlimited)
     */
    fun definir(user: User, restreint: Boolean, salles: Collection<Salle>)

    /** Access of a user created now: limited, with no element, until an ADMIN grants some. */
    fun initialiser(user: User)

    /** What [user] may see right now (nothing for null). */
    fun perimetre(user: User?): Perimetre

    /** [temperatures] restricted to those of the salles / boxes [perimetre] allows. */
    fun filtrerTemperatures(perimetre: Perimetre, temperatures: List<Temperature>): List<Temperature>

    /** Forgets the accesses to [salle] (before deleting it). */
    fun oublierSalle(salle: Salle)

    /** Forgets the accesses of [user] (before deleting them). */
    fun oublierUtilisateur(user: User)
}
