package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.thermobox.persistence.dataservices.SalleSalleAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class SalleSalleAttrDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<SalleSalleAttr>
) : SalleSalleAttrDataService,
    CrudJpaService<SalleSalleAttr>(emf, entityClass) {

    override fun getBySalle(s: Salle): List<SalleSalleAttr> = read { em ->
        em.createQuery(
            "SELECT ssa FROM SalleSalleAttr ssa WHERE ssa.salleA = :salle OR ssa.salleB = :salle",
            SalleSalleAttr::class.java
        ).setParameter("salle", s)
            .resultList
    }
}