package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.utils.exceptions.AlreadyExistsException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Named
import javax.naming.InvalidNameException

/** Inscription publique (POST /api/users n'exige pas d'authentification). */
@Named
@RequestScoped
class RegisterBean {

    var username: String = ""
    var password: String = ""
    var confirm: String = ""

    fun register(): String? {
        val name = username.trim()
        if (password != confirm) {
            Faces.error("Les mots de passe ne correspondent pas")
            return null
        }
        if (runCatching { Services.users.getByUsername(name) }.getOrNull() != null) {
            Faces.error("Ce nom d'utilisateur est déjà pris")
            return null
        }

        // This constructor makes a VIEWER: a self-registered account never gets more rights.
        val user = User(name, password)

        return try {
            Services.users.add(user)
            Faces.keepMessages()
            Faces.info("Compte créé", "Tu peux maintenant te connecter.")
            "/login?faces-redirect=true"
        } catch (e: AlreadyExistsException) {
            Faces.error("Ce nom d'utilisateur est déjà pris")
            null
        } catch (e: IllegalArgumentException) {
            // Password over 72 bytes (accented characters count double), refused before hashing.
            Faces.error("Mot de passe refusé", e.message)
            null
        } catch (e: InvalidNameException) {
            Faces.error("Nom d'utilisateur invalide", "Le nom d'utilisateur ne peux pas contenir d'espace.")
            null
        } catch (e: Exception) {
            Faces.error("Erreur Inconnue", e.message)
            null
        }

    }
}
