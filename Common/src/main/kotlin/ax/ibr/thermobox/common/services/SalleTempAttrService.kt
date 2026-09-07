package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.utils.services.CrudService

interface SalleTempAttrService: CrudService<SalleTempAttr> {

    fun getBySalle(salleId: Int): SalleTempAttr?
    fun getBySalle(salle: Salle): SalleTempAttr?


}