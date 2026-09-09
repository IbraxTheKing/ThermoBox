package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager

class SalleTempAttrDataServiceJPAImpl(pu: String, em: EntityManager,
                                      entityClass: Class<SalleTempAttr>
) : SalleTempAttrDataService, CrudJpaService<SalleTempAttr>(em, entityClass) {

    override fun getBySalle(salleId: Int): SalleTempAttr? {
        TODO("Not yet implemented")
    }

    override fun getBySalle(salle: Salle): SalleTempAttr? {
        TODO("Not yet implemented")
    }
}