package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.thermobox.common.services.SalleSalleAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

class SalleSalleAttrServiceImpl(
    private val salleSalleAttrService: SalleSalleAttrService = PersistenceFactory.getSalleSalleAttrDataService()
) : SalleSalleAttrService, Serializable {

    override fun getBySalle(s: Salle): List<SalleSalleAttr> {
        return salleSalleAttrService.getBySalle(s)
    }

    override fun add(t: SalleSalleAttr) {
        salleSalleAttrService.add(t)
    }

    override fun update(t: SalleSalleAttr) {
        salleSalleAttrService.update(t)
    }

    override fun remove(t: SalleSalleAttr) {
        salleSalleAttrService.remove(t)
    }

    override fun getAll(): List<SalleSalleAttr> {
        return salleSalleAttrService.getAll()
    }

    override fun getById(id: Long): SalleSalleAttr? {
        return salleSalleAttrService.getById(id)
    }
}