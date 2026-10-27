package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.GroupeAcces
import ax.ibr.thermobox.common.entities.GroupeMembre
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.GroupeDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

class GroupeDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<Groupe>
) : GroupeDataService, CrudJpaService<Groupe>(emf, entityClass) {

    override fun getByNom(nom: String): Groupe? = read { em ->
        em.createQuery("SELECT g FROM Groupe g WHERE g.nom = :nom", Groupe::class.java)
            .setParameter("nom", nom).resultList.firstOrNull()
    }

    override fun getMembres(groupe: Groupe): List<User> {
        val id = groupe.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT m.user FROM GroupeMembre m WHERE m.groupe.id = :id", User::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getElements(groupe: Groupe): List<Salle> {
        val id = groupe.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT a.salle FROM GroupeAcces a WHERE a.groupe.id = :id", Salle::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getGroupesDe(user: User): List<Groupe> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery("SELECT m.groupe FROM GroupeMembre m WHERE m.user.id = :id", Groupe::class.java)
                .setParameter("id", id).resultList
        }
    }

    override fun getElementsDesGroupesDe(user: User): List<Salle> {
        val id = user.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT DISTINCT a.salle FROM GroupeAcces a, GroupeMembre m WHERE a.groupe = m.groupe AND m.user.id = :id",
                Salle::class.java
            ).setParameter("id", id).resultList
        }
    }

    override fun remplacer(groupe: Groupe, membres: Collection<Long>, elements: Collection<Long>) {
        val id = requireNotNull(groupe.id) { "Groupe non enregistré" }
        transaction { em ->
            em.createQuery("DELETE FROM GroupeMembre m WHERE m.groupe.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM GroupeAcces a WHERE a.groupe.id = :id").setParameter("id", id).executeUpdate()
            val g = em.getReference(Groupe::class.java, id)
            membres.distinct().forEach { em.persist(GroupeMembre(g, em.getReference(User::class.java, it))) }
            elements.distinct().forEach { em.persist(GroupeAcces(g, em.getReference(Salle::class.java, it))) }
        }
    }

    override fun ajouterMembres(groupe: Groupe, membres: Collection<Long>) {
        val id = requireNotNull(groupe.id) { "Groupe non enregistré" }
        transaction { em ->
            val deja = ids(em, "SELECT m.user.id FROM GroupeMembre m WHERE m.groupe.id = :id", id)
            val g = em.getReference(Groupe::class.java, id)
            membres.distinct().filter { it !in deja }.forEach { em.persist(GroupeMembre(g, em.getReference(User::class.java, it))) }
        }
    }

    override fun retirerMembres(groupe: Groupe, membres: Collection<Long>) {
        val id = groupe.id ?: return
        if (membres.isEmpty()) return
        transaction { em ->
            em.createQuery("DELETE FROM GroupeMembre m WHERE m.groupe.id = :id AND m.user.id IN :users")
                .setParameter("id", id).setParameter("users", membres.distinct()).executeUpdate()
        }
    }

    override fun ajouterElements(groupe: Groupe, elements: Collection<Long>) {
        val id = requireNotNull(groupe.id) { "Groupe non enregistré" }
        transaction { em ->
            val deja = ids(em, "SELECT a.salle.id FROM GroupeAcces a WHERE a.groupe.id = :id", id)
            val g = em.getReference(Groupe::class.java, id)
            elements.distinct().filter { it !in deja }.forEach { em.persist(GroupeAcces(g, em.getReference(Salle::class.java, it))) }
        }
    }

    override fun retirerElements(groupe: Groupe, elements: Collection<Long>) {
        val id = groupe.id ?: return
        if (elements.isEmpty()) return
        transaction { em ->
            em.createQuery("DELETE FROM GroupeAcces a WHERE a.groupe.id = :id AND a.salle.id IN :salles")
                .setParameter("id", id).setParameter("salles", elements.distinct()).executeUpdate()
        }
    }

    override fun supprimer(groupe: Groupe) {
        val id = groupe.id ?: return
        transaction { em ->
            em.createQuery("DELETE FROM GroupeMembre m WHERE m.groupe.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM GroupeAcces a WHERE a.groupe.id = :id").setParameter("id", id).executeUpdate()
            em.createQuery("DELETE FROM Groupe g WHERE g.id = :id").setParameter("id", id).executeUpdate()
        }
    }

    override fun oublierUtilisateur(user: User) {
        val id = user.id ?: return
        transaction { em -> em.createQuery("DELETE FROM GroupeMembre m WHERE m.user.id = :id").setParameter("id", id).executeUpdate() }
    }

    override fun oublierSalle(salle: Salle) {
        val id = salle.id ?: return
        transaction { em -> em.createQuery("DELETE FROM GroupeAcces a WHERE a.salle.id = :id").setParameter("id", id).executeUpdate() }
    }

    private fun ids(em: EntityManager, jpql: String, id: Long): Set<Long> =
        em.createQuery(jpql, java.lang.Long::class.java).setParameter("id", id).resultList.map { it.toLong() }.toSet()
}
