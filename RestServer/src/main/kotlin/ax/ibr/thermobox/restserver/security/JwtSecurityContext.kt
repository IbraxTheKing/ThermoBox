package ax.ibr.thermobox.restserver.security

import ax.ibr.thermobox.common.acces.Perimetre
import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.ws.rs.core.SecurityContext
import java.security.Principal

/**
 * The user and their JWT, carried by the Principal Jersey exposes: a `@Context SecurityContext`
 * injected in a field is a proxy, not this class, so it can't be cast; its Principal can.
 */
data class JwtPrincipal(
    val user: User,
    val token: DecodedJWT
) : Principal {
    override fun getName(): String? = user.username
}

/**
 * Security context installed by the security filter once a request is authenticated.
 * Resources get it through `@Context SecurityContext`; roles come from the database user,
 * not from the token, so a role change applies immediately.
 */
class JwtSecurityContext(
    val user: User,
    val token: DecodedJWT,
    private val secure: Boolean
) : SecurityContext {

    private val principal = JwtPrincipal(user, token)

    override fun getUserPrincipal(): Principal = principal

    override fun isUserInRole(role: String): Boolean = user.type?.hasRole(role) == true

    override fun isSecure(): Boolean = secure

    override fun getAuthenticationScheme(): String = "Bearer"
}

/** Authenticated user of the current request. Only valid on non-public endpoints. */
val SecurityContext.currentUser: User
    get() = (userPrincipal as? JwtPrincipal)?.user
        ?: throw IllegalStateException("Authenticated ThermoBox user not found")

/** JWT of the current request. Only valid on non-public endpoints. */
val SecurityContext.currentToken: DecodedJWT
    get() = (userPrincipal as? JwtPrincipal)?.token
        ?: throw IllegalStateException("Authenticated ThermoBox token not found")

/**
 * What the current user may see. 26.10 has no restricted accesses (they came with 26.11): every
 * user sees every element, so the routes ported from 26.11 answer as there for an unlimited user.
 */
@Suppress("UnusedReceiverParameter")
val SecurityContext.perimetre: Perimetre
    get() = Perimetre.TOUT
