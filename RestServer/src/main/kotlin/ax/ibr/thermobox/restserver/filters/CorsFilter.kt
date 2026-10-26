package ax.ibr.thermobox.restserver.filters

import jakarta.annotation.Priority
import jakarta.ws.rs.HttpMethod
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
class CorsFilter : ContainerRequestFilter, ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext) {
        val isPreflight =
            requestContext.method == HttpMethod.OPTIONS &&
                    requestContext.getHeaderString("Origin") != null &&
                    requestContext.getHeaderString("Access-Control-Request-Method") != null

        if (isPreflight) {
            requestContext.abortWith(Response.noContent().build())
        }
    }

    override fun filter(
        requestContext: ContainerRequestContext,
        responseContext: ContainerResponseContext
    ) {
        responseContext.headers.apply {
            putSingle("Access-Control-Allow-Origin", "*")
            putSingle(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD"
            )
            putSingle(
                "Access-Control-Allow-Headers",
                "Content-Type, Accept, Authorization"
            )
            putSingle("Access-Control-Expose-Headers", "X-Total-Count, ETag")
            putSingle("Access-Control-Max-Age", "86400")
        }
    }
}