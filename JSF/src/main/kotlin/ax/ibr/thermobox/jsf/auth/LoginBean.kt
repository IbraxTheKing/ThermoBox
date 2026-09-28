package ax.ibr.thermobox.jsf.auth

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.enterprise.context.SessionScoped
import jakarta.faces.context.FacesContext
import jakarta.inject.Named
import jakarta.servlet.http.HttpServletRequest
import java.io.Serializable
import java.security.MessageDigest

/**
 * Session de l'utilisateur connecté + règles de rôles.
 * Les règles reprennent celles des resources REST (@RequiresRole / @RequiresAuth).
 */
@Named
@SessionScoped
class LoginBean : Serializable {

    var username: String = ""
    var password: String = ""
    var currentUser: User? = null

    val isLoggedIn: Boolean get() = currentUser != null
    val isAdmin: Boolean get() = hasRole("ADMIN")
    val isGestionnaire: Boolean get() = hasRole("GESTIONNAIRE")

    /** POST/PUT /api/salletemps : ADMIN ou GESTIONNAIRE. */
    val canSetConsigne: Boolean get() = isAdmin || isGestionnaire

    fun isOwner(user: User?): Boolean =
        user != null && currentUser != null && user.id == currentUser?.id

    fun login(): String? {
        val user = runCatching { Services.users.getByUsername(username.trim()) }.getOrNull()
        val ok = user != null && passwordMatches(password, user.password)
        password = ""
        if (!ok) {
            Faces.error("Nom d'utilisateur ou mot de passe incorrect")
            return null
        }
        // Protection contre la fixation de session
        (FacesContext.getCurrentInstance().externalContext.request as HttpServletRequest).changeSessionId()
        currentUser = user
        return "/app/dashboard?faces-redirect=true"
    }

    fun logout(): String {
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

    /** Met à jour l'utilisateur en session après modification de son profil. */
    fun refresh(user: User) {
        currentUser = user
    }

    /** Vérifie un droit côté serveur (le `rendered` des boutons ne suffit pas). */
    fun require(allowed: Boolean): Boolean {
        if (!allowed) Faces.error("Action non autorisée")
        return allowed
    }

    private fun hasRole(role: String): Boolean =
        currentUser?.type?.toString().equals(role, ignoreCase = true)

    /**
     * ⚠️ À adapter à la façon dont ton UserService stocke les mots de passe
     * (ex. BCrypt.checkpw(raw, stored) s'ils sont hashés).
     * Ici : comparaison en temps constant d'un mot de passe stocké tel quel.
     */
    private fun passwordMatches(raw: String, stored: String?): Boolean {
        if (stored == null) return false
        return MessageDigest.isEqual(raw.toByteArray(), stored.toByteArray())
    }
}