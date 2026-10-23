package ax.ibr.thermobox.restserver.security

import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.ws.rs.core.SecurityContext
import java.security.Principal

/**
 * Security context installed by the security filter once a request is authenticated.
 * Resources get it through `@Context SecurityContext`; roles come from the database user,
 * not from the token, so a role change applies immediately.
 */

data class JwtPrincipal(
    val user: User,
    val token: DecodedJWT
) : Principal {
    override fun getName(): String? = user.username
}

class JwtSecurityContext(
    val user: User,
    val token: DecodedJWT,
    private val secure: Boolean
) : SecurityContext {

    private val principal = JwtPrincipal(user, token)

    override fun getUserPrincipal(): Principal = principal

    override fun isUserInRole(role: String): Boolean =
        principal.user.type?.hasRole(role) == true

    override fun isSecure(): Boolean = secure

    override fun getAuthenticationScheme(): String = "Bearer"
}

/** Authenticated user of the current request. Only valid on non-public endpoints. */
val SecurityContext.currentUser: User
    get() = (userPrincipal as? JwtPrincipal)?.user
        ?: throw IllegalStateException("Authenticated ThermoBox user not found")
