package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.UserGroupAccess
import ax.ibr.thermobox.common.entities.UserGroupMember
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

class UserGroupDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<UserGroup>
) : UserGroupDataService, CrudJpaService<UserGroup>(emf, entityClass) {

    override fun getByName(name: String): UserGroup? = read { em ->
        em.createQuery("SELECT g FROM UserGroup g WHERE g.name = :name", UserGroup::class.java)
            .setParameter("name", name).resultList.firstOrNull()
    }

    override fun getMembers(group: UserGroup): List<User> {
        val id = group.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT m.user FROM UserGroupMember m WHERE m.userGroup.id = :id", User::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getElements(group: UserGroup): List<Room> {
        val id = group.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT a.room FROM UserGroupAccess a WHERE a.userGroup.id = :id", Room::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getGroupsOf(user: User): List<UserGroup> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT m.userGroup FROM UserGroupMember m WHERE m.user.id = :id", UserGroup::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getElementsOfGroupsOf(user: User): List<Room> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT DISTINCT a.room FROM UserGroupAccess a, UserGroupMember m WHERE a.userGroup = m.userGroup AND m.user.id = :id",
                Room::class.java
            ).setParameter("id", id).resultList
        }
    }

    override fun replace(group: UserGroup, members: Collection<Long>, elements: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        transaction { em ->
            em.createQuery("DELETE FROM UserGroupMember m WHERE m.userGroup.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM UserGroupAccess a WHERE a.userGroup.id = :id").setParameter("id", id).executeUpdate()
            val g = em.getReference(UserGroup::class.java, id)
            members.distinct().forEach { em.persist(UserGroupMember(g, em.getReference(User::class.java, it))) }
            elements.distinct().forEach { em.persist(UserGroupAccess(g, em.getReference(Room::class.java, it))) }
        }
    }

    override fun addMembers(group: UserGroup, members: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        transaction { em ->
            val already = ids(em, "SELECT m.user.id FROM UserGroupMember m WHERE m.userGroup.id = :id", id)
            val g = em.getReference(UserGroup::class.java, id)
            members.distinct().filter { it !in already }.forEach { em.persist(UserGroupMember(g, em.getReference(User::class.java, it))) }
        }
    }

    override fun removeMembers(group: UserGroup, members: Collection<Long>) {
        val id = group.id ?: return
        if (members.isEmpty()) return
        transaction { em ->
            em.createQuery("DELETE FROM UserGroupMember m WHERE m.userGroup.id = :id AND m.user.id IN :users")
                .setParameter("id", id).setParameter("users", members.distinct()).executeUpdate()
        }
    }

    override fun addElements(group: UserGroup, elements: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        transaction { em ->
            val already = ids(em, "SELECT a.room.id FROM UserGroupAccess a WHERE a.userGroup.id = :id", id)
            val g = em.getReference(UserGroup::class.java, id)
            elements.distinct().filter { it !in already }.forEach { em.persist(UserGroupAccess(g, em.getReference(Room::class.java, it))) }
        }
    }

    override fun removeElements(group: UserGroup, elements: Collection<Long>) {
        val id = group.id ?: return
        if (elements.isEmpty()) return
        transaction { em ->
            em.createQuery("DELETE FROM UserGroupAccess a WHERE a.userGroup.id = :id AND a.room.id IN :rooms")
                .setParameter("id", id).setParameter("rooms", elements.distinct()).executeUpdate()
        }
    }

    override fun deleteGroup(group: UserGroup) {
        val id = group.id ?: return
        transaction { em ->
            em.createQuery("DELETE FROM UserGroupMember m WHERE m.userGroup.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM UserGroupAccess a WHERE a.userGroup.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM UserGroup g WHERE g.id = :id").setParameter("id", id).executeUpdate()
        }
    }

    override fun forgetUser(user: User) {
        val id = user.id ?: return
        transaction { em -> em.createQuery("DELETE FROM UserGroupMember m WHERE m.user.id = :id").setParameter("id", id).executeUpdate() }
    }

    override fun forgetRoom(room: Room) {
        val id = room.id ?: return
        transaction { em -> em.createQuery("DELETE FROM UserGroupAccess a WHERE a.room.id = :id").setParameter("id", id).executeUpdate() }
    }

    private fun ids(em: EntityManager, jpql: String, id: Long): Set<Long> =
        em.createQuery(jpql, java.lang.Long::class.java).setParameter("id", id).resultList.map { it.toLong() }.toSet()
}
