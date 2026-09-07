package ax.ibr.utils.exceptions

/**
 * Exception thrown when an attempt is made to create an entity
 * that already exists.
 *
 * This exception is generally used in business logic to prevent
 * duplicate resources in the application.
 *
 * Example:
 * - Creating a user with an existing username
 *
 * The REST layer should handle this exception and return
 * an HTTP 409 Conflict response.
 *
 * @param message description of the existing resource
 *
 * @author ib
 * @since 1.0
 */
class AlreadyExistsException(
    message: String
) : Exception(message)