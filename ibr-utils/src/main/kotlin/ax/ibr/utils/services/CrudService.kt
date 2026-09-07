package ax.ibr.utils.services

/**
 * Generic CRUD service interface defining common operations
 * for managing entities.
 *
 * This interface provides a standard contract for services
 * that handle persistent objects.
 *
 * Implementations are responsible for:
 * - Data access logic
 * - Transaction management
 * - Entity persistence
 *
 * @param T type of entity managed by the service
 *
 * @author ib
 * @since 1.0
 */
interface CrudService<T> {


    /**
     * Adds a new entity.
     *
     * @param t entity to persist
     */
    fun add(t: T)


    /**
     * Updates an existing entity.
     *
     * @param t entity containing updated values
     */
    fun update(t: T)


    /**
     * Removes an existing entity.
     *
     * @param t entity to delete
     */
    fun remove(t: T)


    /**
     * Retrieves all entities.
     *
     * @return list of all entities
     */
    fun getAll(): List<T>


    /**
     * Retrieves an entity by its identifier.
     *
     * @param id unique identifier of the entity
     * @return the entity if found, otherwise null
     */
    fun getById(id: Long): T?
}