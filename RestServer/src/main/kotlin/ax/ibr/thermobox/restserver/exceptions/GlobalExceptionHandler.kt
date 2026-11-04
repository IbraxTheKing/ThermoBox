package ax.ibr.thermobox.restserver.exceptions

import ax.ibr.thermobox.restserver.dto.ErrorResponse
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.sql.SQLException
import java.time.format.DateTimeParseException

/**
 * The answer to an exception no resource caught.
 *
 * - Jersey's own (WebApplicationException: unknown address 404, method not allowed 405, type not
 *   supported 415, unreadable JSON 400…) keep their status. Mapped like any other Throwable, they
 *   all became 500.
 * - A refused value (IllegalArgumentException) or a date that can't be read is the caller's: 400.
 * - The database's errors are written in the server's log, not sent: their message holds the SQL.
 */
@Provider
class GlobalExceptionHandler : ExceptionMapper<Throwable> {
    override fun toResponse(exception: Throwable): Response {
        if (exception is WebApplicationException) {
            val answer = exception.response
            if (answer.hasEntity()) return answer
            val status = answer.status
            return json(status, answer.statusInfo.reasonPhrase ?: "HTTP $status")
        }
        if (exception is IllegalArgumentException || exception is DateTimeParseException) {
            return json(400, exception.message ?: "Invalid request")
        }
        if (fromDatabase(exception)) {
            System.err.println("[ThermoBox] database error: ${exception.message}")
            exception.printStackTrace()
            return json(500, "Database error")
        }
        return json(500, exception.message ?: "An unexpected error occurred")
    }

    private fun json(status: Int, message: String): Response =
        Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(ErrorResponse(status, message)).build()

    private fun fromDatabase(e: Throwable): Boolean =
        generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(10).any {
            it is SQLException || it.javaClass.name.startsWith("jakarta.persistence.") || it.javaClass.name.startsWith("org.eclipse.persistence.")
        }
}
