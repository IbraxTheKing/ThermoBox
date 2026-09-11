package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.persistence.dataservices.SalleDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager

class SalleDataServiceJPAImpl(pu: String, em: EntityManager,
                              entityClass: Class<Salle>
) : SalleDataService, CrudJpaService<Salle>(em, entityClass) {

    override fun getByName(name: String): Salle? {
        return em.createQuery(
            "SELECT s FROM Salle s WHERE s.name = :name",
            Salle::class.java
        ).setParameter("name", name)
            .resultList
            .firstOrNull()
    }

}