package ax.ibr.utils.services.jpa

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

/**
 * Generic JPA CRUD service providing common database operations
 * for any JPA entity.
 *
 * An [EntityManager] is not thread-safe, so this service never keeps one: every operation
 * opens its own from the shared [EntityManagerFactory] and closes it when done. The service
 * can therefore be used as a singleton by concurrent requests. Returned entities are detached.
 *
 * Example:
 * ```
 * class UserService(
 *     emf: EntityManagerFactory
 * ) : CrudJpaService<User>(emf, User::class.java)
 * ```
 *
 * @param T entity type managed by this service
 * @param emf application-wide factory the entity managers are created from
 * @param entityClass class reference of the managed entity
 *
 * @author ib
 * @since 1.0
 */
open class CrudJpaService<T>(
    protected val emf: EntityManagerFactory,
    private val entityClass: Class<T>
) {
    private val entityName = entityClass.simpleName

    /** Runs a read-only [block] with a short-lived entity manager. */
    protected fun <R> read(block: (EntityManager) -> R): R {
        val em = emf.createEntityManager()
        try {
            return block(em)
        } finally {
            em.close()
        }
    }

    /** Runs [block] in its own transaction: committed on success, rolled back on any exception. */
    protected fun <R> transaction(block: (EntityManager) -> R): R {
        val em = emf.createEntityManager()
        val tx = em.transaction
        try {
            tx.begin()
            val result = block(em)
            tx.commit()
            return result
        } catch (e: Exception) {
            if (tx.isActive) tx.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    open fun add(t: T) {
        transaction { it.persist(t) }
    }

    open fun update(t: T) {
        transaction { it.merge(t) }
    }

    open fun remove(t: T) {
        // Entities are always detached here: re-attach before removing.
        transaction { it.remove(it.merge(t)) }
    }

    open fun getAll(): List<T> = read {
        it.createQuery("SELECT e FROM $entityName e", entityClass).resultList
    }

    open fun getById(id: Long): T? = read {
        it.find(entityClass, id)
    }
}
