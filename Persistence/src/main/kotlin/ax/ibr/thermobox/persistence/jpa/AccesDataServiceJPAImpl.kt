package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Acces
import ax.ibr.thermobox.common.entities.AccesRestreint
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.AccesDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class AccesDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<Acces>
) : AccesDataService, CrudJpaService<Acces>(emf, entityClass) {

    override fun getByUser(user: User): List<Acces> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT a FROM Acces a WHERE a.user.id = :id", Acces::class.java)
                .setParameter("id", id)
                .resultList
        }
    }

    override fun estRestreint(user: User): Boolean {
        val id = user.id ?: return true
        return read { em ->
            em.createQuery("SELECT COUNT(r) FROM AccesRestreint r WHERE r.user.id = :id", Long::class.javaObjectType)
                .setParameter("id", id)
                .singleResult > 0
        }
    }

    override fun remplacer(user: User, restreint: Boolean, salleIds: Collection<Long>) {
        val id = requireNotNull(user.id) { "Utilisateur non enregistré" }
        transaction { em ->
            em.createQuery("DELETE FROM Acces a WHERE a.user.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM AccesRestreint r WHERE r.user.id = :id").setParameter("id", id).executeUpdate()
            if (restreint) {
                val u = em.getReference(User::class.java, id)
                em.persist(AccesRestreint(u))
                salleIds.distinct().forEach { salleId -> em.persist(Acces(u, em.getReference(Salle::class.java, salleId))) }
            }
        }
    }

    override fun supprimerPourSalle(salle: Salle) {
        val id = salle.id ?: return
        transaction { em -> em.createQuery("DELETE FROM Acces a WHERE a.salle.id = :id").setParameter("id", id).executeUpdate() }
    }

    override fun supprimerPourUtilisateur(user: User) {
        val id = user.id ?: return
        transaction { em ->
            em.createQuery("DELETE FROM Acces a WHERE a.user.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM AccesRestreint r WHERE r.user.id = :id").setParameter("id", id).executeUpdate()
        }
    }
}
