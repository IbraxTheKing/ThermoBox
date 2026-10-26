
package ax.ibr.thermobox.restserver.security

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.access.AccessScope
import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.ws.rs.core.SecurityContext
import java.security.Principal

/**
 * Carries the user and their JWT in the Principal Jersey exposes,
 * without depending on Jersey's internal SecurityContext implementation.
 */
data class JwtPrincipal(
    val user: User,
    val token: DecodedJWT
) : Principal {
    override fun getName(): String? = user.username
}

class JwtSecurityContext(
    user: User,
    token: DecodedJWT,
    private val secure: Boolean
) : SecurityContext {

    private val principal = JwtPrincipal(user, token)

    override fun getUserPrincipal(): Principal = principal

    override fun isUserInRole(role: String): Boolean =
        principal.user.type?.hasRole(role) == true

    override fun isSecure(): Boolean = secure

    override fun getAuthenticationScheme(): String = "Bearer"
}

/**
 * Extensions to read the data from the Principal,
 * without casting the SecurityContext itself.
 */
val SecurityContext.currentUser: User
    get() = (userPrincipal as? JwtPrincipal)?.user
        ?: throw IllegalStateException("Authenticated ThermoBox user not found")

val SecurityContext.currentToken: DecodedJWT
    get() = (userPrincipal as? JwtPrincipal)?.token
        ?: throw IllegalStateException("Authenticated ThermoBox token not found")

val SecurityContext.scope: AccessScope
    get() = BusinessFactory.getAccessService().scope(currentUser)
