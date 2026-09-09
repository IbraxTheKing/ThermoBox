package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class SalleTempAttrServiceImpl : SalleTempAttrService {

    private val salleTempAttrService: SalleTempAttrService = PersistenceFactory().getSalleTempAttrDataService()

    override fun getBySalle(salleId: Long): SalleTempAttr? {
        return salleTempAttrService.getBySalle(salleId)
    }

    override fun getBySalle(salle: Salle): SalleTempAttr? {
        return salleTempAttrService.getBySalle(salle)
    }

    override fun add(t: SalleTempAttr) {
        salleTempAttrService.add(t)
    }

    override fun update(t: SalleTempAttr) {
        salleTempAttrService.update(t)
    }

    override fun remove(t: SalleTempAttr) {
        salleTempAttrService.remove(t)
    }

    override fun getAll(): List<SalleTempAttr> {
        return salleTempAttrService.getAll()
    }

    override fun getById(id: Long): SalleTempAttr? {
        return salleTempAttrService.getById(id)
    }

}
