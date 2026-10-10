package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.utils.services.CrudService

interface SalleSalleAttrService : CrudService<SalleSalleAttr> {

    fun getBySalle(s: Salle) : List<SalleSalleAttr>
}