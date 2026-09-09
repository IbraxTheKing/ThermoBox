package ax.ibr.thermobox.restserver.filters

import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.container.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

@Provider
class SecurityFilter : ContainerRequestFilter {

    @Context
    private lateinit var resourceInfo: ResourceInfo

    override fun filter(requestContext: ContainerRequestContext) {
        val method = resourceInfo.resourceMethod ?: return

        val authAnnotation = method.getAnnotation(RequiresAuth::class.java)
        val roleAnnotation = method.getAnnotation(RequiresRole::class.java)

        // Si aucune sécurité n'est requise, on laisse passer
        if (authAnnotation == null && roleAnnotation == null) {
            return
        }

        // 1. Récupérer l'utilisateur actuel
        val currentUser = getCurrentUser() ?: throw RuntimeException("Not Authenticated")
        val userType = currentUser.type ?: throw RuntimeException("User type is missing")

        // 2. Vérification de @RequiresRole
        roleAnnotation?.let {
            // On vérifie si le type de l'utilisateur possède le rôle requis
            if (!userType.hasRole(it.value)) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build())
                return
            }
        }

        // 3. Vérification de @RequiresAuth
        authAnnotation?.let {
            // Vérification : L'utilisateur doit avoir AU MOINS UN des rôles spécifiés
            val hasAnyRole = it.roles.isNotEmpty() && it.roles.any { role -> userType.hasRole(role) }

            if (!hasAnyRole) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build())
                return
            }

            // Vérification du propriétaire (Owner-based)
            if (it.allowOwner) {
                val ownerId = extractOwnerId(requestContext, it.ownerParam)
                if (ownerId != null && ownerId != currentUser.id) {
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build())
                    return
                }
            }
        }
    }

    private fun getCurrentUser(): User? {
        // À implémenter : récupération du user via JWT ou Session
        return null
    }

    private fun extractOwnerId(context: ContainerRequestContext, paramName: String): Long? {
        // Récupère l'ID depuis les paramètres de l'URL (ex: /salles/5)
        val pathParams = context.uriInfo.pathParameters
        return pathParams.getFirst(paramName)?.toLongOrNull()
    }
}