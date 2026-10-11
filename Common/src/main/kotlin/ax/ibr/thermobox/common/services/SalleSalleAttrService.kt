package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.utils.services.CrudService

interface SalleSalleAttrService : CrudService<SalleSalleAttr> {

    fun getBySalle(s: Salle) : List<SalleSalleAttr>

    /** Direct children of [parent] (see [SalleSalleAttr] for the link direction). */
    fun getChildren(parent: Salle): List<Salle>

    /** Direct parent of [child], or null if it is not attached. */
    fun getParent(child: Salle): Salle?
}