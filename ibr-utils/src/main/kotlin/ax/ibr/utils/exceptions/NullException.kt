package ax.ibr.utils.exceptions

/**
 * Exception thrown when a null value is encountered in a context where it is not expected.
 *
 * @param message The detail message describing the reason for the exception.
 */
class NullException(
    message: String
) : Exception(message)