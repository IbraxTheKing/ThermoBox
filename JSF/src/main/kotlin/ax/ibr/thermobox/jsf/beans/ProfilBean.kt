package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.utils.exceptions.AlreadyExistsException
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.io.Serializable

/** Modification / suppression de son propre compte (allowOwner = true dans UserResource). */
@Named
@ViewScoped
class ProfilBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    var user: User? = null
    var newPassword: String = ""
    var confirmPassword: String = ""

    @PostConstruct
    fun load() {
        user = login.currentUser?.id?.let { Services.users.getById(it) }
    }

    fun save() {
        val u = user ?: return
        if (!login.require(login.isOwner(u))) return

        if (newPassword.isNotBlank()) {
            if (newPassword != confirmPassword) {
                Faces.error("Les mots de passe ne correspondent pas")
                return
            }
            u.password = newPassword
        }
        try {
            Services.users.update(u)
            login.refresh(u)
            newPassword = ""
            confirmPassword = ""
            Faces.info("Profil mis à jour")
        } catch (e: AlreadyExistsException) {
            Faces.error("Ce nom d'utilisateur est déjà pris", e.message)
        } catch (e: Exception) {
            Faces.error("Mise à jour impossible", e.message)
        }
    }

    fun deleteAccount(): String? {
        val u = user ?: return null
        if (!login.require(login.isOwner(u))) return null
        return try {
            Services.users.remove(u)
            login.logout()
            "/login?faces-redirect=true&compteSupprime=true"
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
            null
        }
    }
}
