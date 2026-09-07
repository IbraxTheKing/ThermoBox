package ax.ibr.thermobox.restserver.filters

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

@Provider
class CorsFilter : ContainerResponseFilter {

    override fun filter(
        requestContext: ContainerRequestContext,
        responseContext: ContainerResponseContext
    ) {
        responseContext.headers.apply {
            putSingle("Access-Control-Allow-Origin", "*")
            putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
            putSingle("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization")
            putSingle("Access-Control-Max-Age", "86400")
        }
    }
}