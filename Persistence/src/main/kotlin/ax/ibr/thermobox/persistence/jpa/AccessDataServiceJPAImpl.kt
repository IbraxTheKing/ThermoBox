package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.RestrictedAccess
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.AccessDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class AccessDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<Access>
) : AccessDataService, CrudJpaService<Access>(emf, entityClass) {

    override fun getByUser(user: User): List<Access> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT a FROM Access a WHERE a.user.id = :id", Access::class.java)
                .setParameter("id", id)
                .resultList
        }
    }

    override fun isRestricted(user: User): Boolean {
        val id = user.id ?: return true
        return read { em ->
            em.createQuery("SELECT COUNT(r) FROM RestrictedAccess r WHERE r.user.id = :id", Long::class.javaObjectType)
                .setParameter("id", id)
                .singleResult > 0
        }
    }

    override fun replace(user: User, restricted: Boolean, roomIds: Collection<Long>) {
        val id = requireNotNull(user.id) { "User not saved" }
        transaction { em ->
            em.createQuery("DELETE FROM Access a WHERE a.user.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM RestrictedAccess r WHERE r.user.id = :id").setParameter("id", id).executeUpdate()
            if (restricted) {
                val u = em.getReference(User::class.java, id)
                em.persist(RestrictedAccess(u))
                roomIds.distinct().forEach { roomId -> em.persist(Access(u, em.getReference(Room::class.java, roomId))) }
            }
        }
    }

    override fun deleteForRoom(room: Room) {
        val id = room.id ?: return
        transaction { em -> em.createQuery("DELETE FROM Access a WHERE a.room.id = :id").setParameter("id", id).executeUpdate() }
    }

    override fun deleteForUser(user: User) {
        val id = user.id ?: return
        transaction { em ->
            em.createQuery("DELETE FROM Access a WHERE a.user.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM RestrictedAccess r WHERE r.user.id = :id").setParameter("id", id).executeUpdate()
        }
    }
}
