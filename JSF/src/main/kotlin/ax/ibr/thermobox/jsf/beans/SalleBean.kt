package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Salle
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

/** Liste des salles + CRUD (ADMIN), équivalent de SalleResource. */
@Named
@ViewScoped
class SalleBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    var salles: List<Salle> = emptyList()
    var selected: Salle? = null

    @PostConstruct
    fun load() {
        salles = Services.salles.getAll()
    }

    fun create() {
        selected = Salle("")
    }

    fun edit(salle: Salle) {
        // Copie fraîche : "Annuler" ne laisse pas la liste modifiée
        selected = Services.salles.getById(salle.id!!)
    }

    fun save() {
        if (!login.require(login.isAdmin)) return
        val salle = selected ?: return
        try {
            if (isNew(salle.id)) {
                Services.salles.add(salle)
                Faces.info("Salle « ${salle.name} » créée")
            } else {
                Services.salles.update(salle)
                Faces.info("Salle « ${salle.name} » modifiée")
            }
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            load()
        } catch (e: AlreadyExistsException) {
            Faces.error("Cette salle existe déjà", e.message)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
    }

    fun delete(salle: Salle) {
        if (!login.require(login.isAdmin)) return
        try {
            Services.salles.getById(salle.id!!)?.let { Services.salles.remove(it) }
            Faces.info("Salle « ${salle.name} » supprimée")
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
        }
        load()
    }
}
