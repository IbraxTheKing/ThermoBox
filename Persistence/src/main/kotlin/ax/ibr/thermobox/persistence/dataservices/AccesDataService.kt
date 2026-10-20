package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Acces
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

/** Rows of "acces" and "acces_restreint". */
interface AccesDataService : CrudService<Acces> {

    fun getByUser(user: User): List<Acces>

    fun estRestreint(user: User): Boolean

    /** Replaces in one transaction: the restriction marker and every access of [user]. */
    fun remplacer(user: User, restreint: Boolean, salleIds: Collection<Long>)

    fun supprimerPourSalle(salle: Salle)

    fun supprimerPourUtilisateur(user: User)
}
