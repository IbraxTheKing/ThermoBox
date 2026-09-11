package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager

class SalleTempAttrDataServiceJPAImpl(pu: String, em: EntityManager,
                                      entityClass: Class<SalleTempAttr>
) : SalleTempAttrDataService, CrudJpaService<SalleTempAttr>(em, entityClass) {

    override fun getBySalle(salleId: Long): SalleTempAttr? {
        // Retourne l'enregistrement le plus récent pour cette salle
        return em.createQuery(
            "SELECT sta FROM SalleTempAttr sta WHERE sta.salle.id = :salleId ORDER BY sta.temperature.date DESC",
            SalleTempAttr::class.java
        ).setParameter("salleId", salleId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
    }

    override fun getBySalle(salle: Salle): SalleTempAttr? {
        val salleId = salle.id ?: return null
        return getBySalle(salleId)
    }
}