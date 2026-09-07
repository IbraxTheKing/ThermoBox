package ax.ibr.utils.services.jpa

import jakarta.persistence.EntityManager

/**
 * Generic JPA CRUD service providing common database operations
 * for any JPA entity.
 *
 * This class handles basic persistence operations:
 * - Create (persist)
 * - Read (find, getAll)
 * - Update (merge)
 * - Delete (remove)
 *
 * Each write operation manages its own transaction:
 * - Starts a transaction before modifying data
 * - Commits on success
 * - Rolls back if an exception occurs
 *
 * This class is designed to be extended by specific services
 * that require custom business logic.
 *
 * Example:
 * ```
 * class UserService(
 *     em: EntityManager
 * ) : CrudJpaService<User>(em, User::class.java)
 * ```
 *
 * @param T entity type managed by this service
 * @param em JPA EntityManager used to access the persistence context
 * @param entityClass class reference of the managed entity
 *
 * @author ib
 * @since 1.0
 */
open class CrudJpaService<T>(
    protected val em: EntityManager,
    private val entityClass: Class<T>

) {
    private var entityName = entityClass.simpleName

    /**
     * Persists a new entity into the database.
     *
     * @param t entity to save
     * @throws Exception if persistence fails
     */
    open fun add(t: T) {

        val tx = em.transaction

        try {

            tx.begin()

            em.persist(t)

            tx.commit()


        } catch (e: Exception) {

            if (tx.isActive)
                tx.rollback()

            throw e
        }
    }


    /**
     * Updates an existing entity.
     *
     * Uses JPA merge operation to synchronize
     * the entity state with the database.
     *
     * @param t entity containing updated values
     */
    open fun update(t: T) {

        val tx = em.transaction

        try {

            tx.begin()

            em.merge(t)

            tx.commit()


        } catch (e: Exception) {

            if (tx.isActive)
                tx.rollback()

            throw e
        }
    }


    /**
     * Removes an entity from the database.
     *
     * If the entity is detached, it will first be
     * attached again using merge().
     *
     * @param t entity to delete
     */
    open fun remove(t: T) {

        val tx = em.transaction

        try {

            tx.begin()

            em.remove(
                if (em.contains(t))
                    t
                else
                    em.merge(t)
            )

            tx.commit()


        } catch (e: Exception) {

            if (tx.isActive)
                tx.rollback()

            throw e
        }
    }


    /**
     * Retrieves all entities of the current type.
     *
     * @return list containing all persisted entities
     */
    open fun getAll(): List<T> {

        return em.createQuery(
            "SELECT e FROM $entityName e",
            entityClass
        ).resultList

    }


    /**
     * Finds an entity by its primary key.
     *
     * @param id identifier of the entity
     * @return the entity if found, otherwise null
     */
    open fun getById(id: Long): T? {

        return em.find(
            entityClass,
            id
        )
    }

}