package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.FavoriDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class FavoriDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<Favori>
) : FavoriDataService, CrudJpaService<Favori>(emf, entityClass) {

    override fun getByUser(user: User): List<Favori> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT f FROM Favori f WHERE f.user.id = :id", Favori::class.java)
                .setParameter("id", id)
                .resultList
        }
    }

    override fun getBySalle(salle: Salle): List<Favori> {
        val id = salle.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT f FROM Favori f WHERE f.salle.id = :id", Favori::class.java)
                .setParameter("id", id)
                .resultList
        }
    }
}
