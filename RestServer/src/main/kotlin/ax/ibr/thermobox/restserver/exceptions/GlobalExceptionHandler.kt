package ax.ibr.thermobox.restserver.exceptions

import ax.ibr.thermobox.restserver.dto.ErrorResponse
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.lang.RuntimeException

@Provider
class GlobalExceptionHandler : ExceptionMapper<Throwable> {
    override fun toResponse(exception: Throwable): Response {
        val status = when (exception) {
            is IllegalArgumentException -> 400
            is RuntimeException -> 500
            else -> 500
        }
        
        val message = exception.message ?: "An unexpected error occurred"
        return Response.status(status).entity(ErrorResponse(status, message)).build()
    }
}