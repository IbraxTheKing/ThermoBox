package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.persistence.dataservices.SalleDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class SalleDataServiceJPAImpl(emf: EntityManagerFactory,
                              entityClass: Class<Salle>
) : SalleDataService, CrudJpaService<Salle>(emf, entityClass) {

    override fun getByName(name: String): Salle? = read { em ->
        em.createQuery(
            "SELECT s FROM Salle s WHERE s.name = :name",
            Salle::class.java
        ).setParameter("name", name)
            .resultList
            .firstOrNull()
    }

}
