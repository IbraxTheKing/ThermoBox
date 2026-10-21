
package ax.ibr.thermobox.restserver.security

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.acces.Perimetre
import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.ws.rs.core.SecurityContext
import java.security.Principal

/**
 * Transporte l'utilisateur et son JWT dans le Principal exposé par Jersey.
 * On ne dépend pas de l'implémentation interne du SecurityContext de Jersey.
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
 * Extensions pour récupérer les données depuis le Principal,
 * sans caster le SecurityContext lui-même.
 */
val SecurityContext.currentUser: User
    get() = (userPrincipal as? JwtPrincipal)?.user
        ?: throw IllegalStateException("Authenticated ThermoBox user not found")

val SecurityContext.currentToken: DecodedJWT
    get() = (userPrincipal as? JwtPrincipal)?.token
        ?: throw IllegalStateException("Authenticated ThermoBox token not found")

val SecurityContext.perimetre: Perimetre
    get() = BusinessFactory.getAccesService().perimetre(currentUser)
