package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.persistence.dataservices.SalleDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class SalleDataServiceJPAImpl(emf: EntityManagerFactory,
                              entityClass: Class<Salle>
) : SalleDataService, CrudJpaService<Salle>(emf, entityClass) {

    override fun getAllByType(c: Salle): List<Salle> = read { em ->
        em.createQuery(
            "SELECT s FROM Salle s WHERE TYPE(s) = :type",
            Salle::class.java
        ).setParameter("type", c.javaClass)
            .resultList
    }
    override fun getByName(name: String): Salle? = read { em ->
        em.createQuery(
            "SELECT s FROM Salle s WHERE s.name = :name",
            Salle::class.java
        ).setParameter("name", name)
            .resultList
            .firstOrNull()
    }

}
