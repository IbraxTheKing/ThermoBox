package ax.ibr.utils.exceptions

/**
 * Exception thrown when a user attempts an unauthorized operation.
 *
 * This exception should be handled by the REST layer and converted
 * to an HTTP 403 Forbidden response.
 *
 * @author ib
 * @since 1.0
 */
class PermissionDeniedException(
    message: String
) : Exception(message)