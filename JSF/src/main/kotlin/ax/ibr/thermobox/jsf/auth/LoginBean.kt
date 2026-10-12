package ax.ibr.thermobox.jsf.auth

import ax.ibr.thermobox.business.security.Authenticator
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.enterprise.context.RequestScoped
import jakarta.faces.context.FacesContext
import jakarta.inject.Named
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Login form, and the current user as authenticated by [AuthFilter] from the JWT cookie.
 * Request-scoped on purpose: nothing about the user lives in the HTTP session.
 * The role rules mirror the REST resources (@RequiresRole / @RequiresAuth).
 */
@Named
@RequestScoped
class LoginBean {

    var username: String = ""
    var password: String = ""

    private val auth: AuthenticatedUser?
        get() = request().getAttribute(AuthFilter.CURRENT_USER) as? AuthenticatedUser

    val currentUser: User? get() = auth?.user

    val isLoggedIn: Boolean get() = auth != null

    /** First letter of the username, shown in the avatar. */
    val initiale: String get() = currentUser?.username?.trim()?.firstOrNull()?.uppercase().orEmpty()
    val isAdmin: Boolean get() = auth?.hasRole("ADMIN") == true

    /** Like hasRole("GESTIONNAIRE") in REST: an ADMIN is also a GESTIONNAIRE. */
    val isGestionnaire: Boolean get() = auth?.hasRole("GESTIONNAIRE") == true

    /** PUT /api/salletemps/room/{id}/consigne : ADMIN or GESTIONNAIRE. */
    val canSetConsigne: Boolean get() = isGestionnaire

    fun isOwner(user: User?): Boolean =
        user?.id != null && user.id == currentUser?.id

    fun login(): String? {
        val user = Authenticator.authenticate(username, password)
        password = ""
        if (user == null) {
            Faces.error("Nom d'utilisateur ou mot de passe incorrect")
            return null
        }
        val req = request()
        // The session only holds JSF view state now, but a fixed id must still not survive login.
        if (req.getSession(false) != null) req.changeSessionId()
        AuthCookie.write(req, response(), JwtService.issue(user).token, JwtService.tokenTtl)
        return "/app/dashboard?faces-redirect=true"
    }

    /** Revokes the token, so a copy of the cookie stops working too. */
    fun logout(): String {
        auth?.let { JwtService.revoke(it.token) }
        AuthCookie.clear(request(), response())
        FacesContext.getCurrentInstance().externalContext.invalidateSession()
        return "/login?faces-redirect=true"
    }

    /** f:viewAction de login/register : inutile d'y rester si déjà connecté. */
    fun redirectIfLoggedIn() {
        if (isLoggedIn) redirect("/app/dashboard.xhtml")
    }

    /** f:viewAction de la page racine index.xhtml */
    fun goHome() {
        redirect(if (isLoggedIn) "/app/dashboard.xhtml" else "/login.xhtml")
    }

    /**
     * Redirection HTTP directe, sans passer par le NavigationHandler :
     * dans un f:viewAction, Mojarra 4.0 plante sur le Flash (NPE ELFlash.setKeepMessages).
     */
    private fun redirect(path: String) {
        val ctx = FacesContext.getCurrentInstance()
        val ec = ctx.externalContext
        ec.redirect(ec.requestContextPath + path)
        ctx.responseComplete()
    }

    /** Vérifie un droit côté serveur (le `rendered` des boutons ne suffit pas). */
    fun require(allowed: Boolean): Boolean {
        if (!allowed) Faces.error("Action non autorisée")
        return allowed
    }

    private fun request() = FacesContext.getCurrentInstance().externalContext.request as HttpServletRequest

    private fun response() = FacesContext.getCurrentInstance().externalContext.response as HttpServletResponse
}
