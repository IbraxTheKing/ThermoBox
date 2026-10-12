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

    override fun getChildren(parent: Salle): List<Salle> {
        val id = parent.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT ssa.salleB FROM SalleSalleAttr ssa WHERE ssa.salleA.id = :id",
                Salle::class.java
            ).setParameter("id", id)
                .resultList
        }
    }

    override fun getParent(child: Salle): Salle? {
        val id = child.id ?: return null
        return read { em ->
            em.createQuery(
                "SELECT ssa.salleA FROM SalleSalleAttr ssa WHERE ssa.salleB.id = :id",
                Salle::class.java
            ).setParameter("id", id)
                .setMaxResults(1)
                .resultList
                .firstOrNull()
        }
    }
}