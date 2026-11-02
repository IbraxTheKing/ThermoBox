package ax.ibr.thermobox.restserver.filters

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.restserver.security.AllowedWithProvisionalPassword
import ax.ibr.thermobox.restserver.security.JwtSecurityContext
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.business.security.ProvisionalPasswords
import ax.ibr.utils.rest.PublicEndpoint
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.annotation.Priority
import jakarta.ws.rs.HttpMethod
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

/**
 * Authenticates every request with a `Bearer` JWT, then applies [RequiresRole] / [RequiresAuth].
 *
 * Endpoints are protected by default: only those annotated with [PublicEndpoint] skip
 * authentication, so a new endpoint can't be exposed by forgetting an annotation.
 *
 * A token is accepted only if it is validly signed, not expired, not revoked (logout) and
 * still points to an existing user. Missing/invalid credentials give 401, insufficient rights 403.
 *
 * A user whose password is provisional (imported account, password reset by an admin) only
 * reaches the endpoints marked [AllowedWithProvisionalPassword] until they choose theirs: the
 * others answer 403 with `"code": "password_change_required"` (401 `provisional_expired` once the
 * provisional password is out of date).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
class SecurityFilter : ContainerRequestFilter {

    @Context
    private lateinit var resourceInfo: ResourceInfo

    private val userService = BusinessFactory.getUserService()

    override fun filter(requestContext: ContainerRequestContext) {
        // CORS preflights never carry the Authorization header.
        if (requestContext.method == HttpMethod.OPTIONS) return

        // No matched resource method: Jersey will answer 404/405 itself.
        val method = resourceInfo.resourceMethod ?: return
        if (method.isAnnotationPresent(PublicEndpoint::class.java) ||
            resourceInfo.resourceClass?.isAnnotationPresent(PublicEndpoint::class.java) == true
        ) return

        val token = extractBearerToken(requestContext)
            ?: return unauthorized(requestContext, "Missing bearer token")
        val jwt = JwtService.verify(token)
            ?: return unauthorized(requestContext, "Invalid or expired token")
        val user = jwt.subject.toLongOrNull()?.let { userService.getById(it) }
            ?: return unauthorized(requestContext, "User no longer exists")
        val userType = user.type
            ?: return forbidden(requestContext)

        requestContext.securityContext =
            JwtSecurityContext(user, jwt, requestContext.securityContext?.isSecure == true)

        if (user.passwordChangeRequired && !method.isAnnotationPresent(AllowedWithProvisionalPassword::class.java)) {
            if (ProvisionalPasswords.expired(user)) return unauthorized(requestContext, "Provisional password expired", "provisional_expired")
            return abort(requestContext, Response.Status.FORBIDDEN, "Password change required", "password_change_required")
        }

        method.getAnnotation(RequiresRole::class.java)?.let {
            if (!userType.hasRole(it.value)) return forbidden(requestContext)
        }

        method.getAnnotation(RequiresAuth::class.java)?.let {
            val hasRole = it.roles.isEmpty() || it.roles.any { role -> userType.hasRole(role) }
            val isOwner = it.allowOwner && isOwner(requestContext, it.ownerParam, user)
            if (!hasRole && !isOwner) return forbidden(requestContext)
        }
    }

    private fun extractBearerToken(context: ContainerRequestContext): String? {
        val header = context.getHeaderString(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring("Bearer ".length).trim().ifEmpty { null }
    }

    private fun isOwner(context: ContainerRequestContext, paramName: String, user: User): Boolean {
        val ownerId = context.uriInfo.pathParameters.getFirst(paramName)?.toLongOrNull()
        return ownerId != null && ownerId == user.id
    }

    private fun unauthorized(context: ContainerRequestContext, message: String, code: String? = null) {
        context.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(if (code == null) mapOf("error" to message) else mapOf("error" to message, "code" to code))
                .build()
        )
    }

    private fun abort(context: ContainerRequestContext, status: Response.Status, message: String, code: String) {
        context.abortWith(
            Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapOf("error" to message, "code" to code))
                .build()
        )
    }

    private fun forbidden(context: ContainerRequestContext) {
        context.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapOf("error" to "Forbidden"))
                .build()
        )
    }
}
