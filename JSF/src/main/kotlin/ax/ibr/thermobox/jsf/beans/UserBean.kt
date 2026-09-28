package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.isNew
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.utils.exceptions.AlreadyExistsException
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import java.io.Serializable

/** Gestion des utilisateurs (ADMIN), équivalent de UserResource. */
@Named
@ViewScoped
class UserBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** ⚠️ Adapte aux valeurs réelles de User.role */
    val roles: List<String> = listOf("ADMIN", "GESTIONNAIRE", "USER")

    var users: List<User> = emptyList()
    var selected: User? = null
    var newPassword: String = ""

    val isCreating: Boolean get() = isNew(selected?.id)

    @PostConstruct
    fun load() {
        users = Services.users.getAll()
    }

    fun isSelf(user: User): Boolean = login.isOwner(user)

    fun create() {
        selected = User()
        newPassword = ""
    }

    fun edit(user: User) {
        selected = Services.users.getById(user.id!!)
        newPassword = ""
    }

    fun save() {
        if (!login.require(login.isAdmin)) return
        val user = selected ?: return
        val creating = isNew(user.id)

        if (newPassword.isNotBlank()) {
            user.password = newPassword
        } else if (creating) {
            Faces.error("Le mot de passe est obligatoire")
            return
        }

        try {
            if (creating) {
                Services.users.add(user)
                Faces.info("Utilisateur « ${user.username} » créé")
            } else {
                Services.users.update(user)
                if (login.isOwner(user)) login.refresh(user)
                Faces.info("Utilisateur « ${user.username} » modifié")
            }
            newPassword = ""
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            load()
        } catch (e: AlreadyExistsException) {
            Faces.error("Ce nom d'utilisateur est déjà pris", e.message)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
    }

    fun delete(user: User) {
        if (!login.require(login.isAdmin)) return
        if (login.isOwner(user)) {
            Faces.error("Supprime ton propre compte depuis la page Profil")
            return
        }
        try {
            Services.users.getById(user.id!!)?.let { Services.users.remove(it) }
            Faces.info("Utilisateur « ${user.username} » supprimé")
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
        }
        load()
    }
}
