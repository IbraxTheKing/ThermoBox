package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.FavoriteDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class FavoriteDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<Favorite>
) : FavoriteDataService, CrudJpaService<Favorite>(emf, entityClass) {

    override fun getByUser(user: User): List<Favorite> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT f FROM Favorite f WHERE f.user.id = :id", Favorite::class.java)
                .setParameter("id", id)
                .resultList
        }
    }

    override fun getByRoom(room: Room): List<Favorite> {
        val id = room.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT f FROM Favorite f WHERE f.room.id = :id", Favorite::class.java)
                .setParameter("id", id)
                .resultList
        }
    }
}
