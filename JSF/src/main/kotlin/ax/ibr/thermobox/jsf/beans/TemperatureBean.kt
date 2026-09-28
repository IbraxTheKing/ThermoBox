package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.utils.exceptions.AlreadyExistsException
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import java.io.Serializable
import java.time.LocalDateTime

/** Liste / filtre / moyennes des températures + CRUD (ADMIN), équivalent de TemperatureResource. */
@Named
@ViewScoped
class TemperatureBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** "tous" | "consigne" | "mesurer" */
    var filtre: String = "tous"
    var temperatures: List<Temperature> = emptyList()
    var moyenneConsigne: Temperature? = null
    var moyenneMesure: Temperature? = null

    // Formulaire du dialogue
    var editing: Temperature? = null
    var formType: String = "mesurer"
    var formValue: Float? = null
    var formDate: LocalDateTime? = null

    val isEditMode: Boolean get() = editing != null

    @PostConstruct
    fun load() {
        temperatures = when (filtre) {
            "consigne", "mesurer" -> Services.temperatures.getByType(sample(filtre)).orEmpty()
            else -> Services.temperatures.getAll()
        }.sortedByDescending { it.date }

        moyenneConsigne = runCatching { Services.temperatures.getAverage(sample("consigne")) }.getOrNull()
        moyenneMesure = runCatching { Services.temperatures.getAverage(sample("mesurer")) }.getOrNull()
    }

    fun typeOf(t: Temperature): String = when (t) {
        is Consigne -> "Consigne"
        is Mesurer -> "Mesure"
        else -> "—"
    }

    fun create() {
        editing = null
        formType = "mesurer"
        formValue = null
        formDate = LocalDateTime.now().withSecond(0).withNano(0)
    }

    fun edit(t: Temperature) {
        editing = t
        formType = if (t is Consigne) "consigne" else "mesurer"
        formValue = t.value
        formDate = t.date
    }

    fun save() {
        if (!login.require(login.isAdmin)) return
        val value = formValue ?: return
        val date = formDate ?: return
        try {
            val t = editing
            if (t == null) {
                Services.temperatures.add(if (formType == "consigne") Consigne(value, date) else Mesurer(value, date))
                Faces.info("Température ajoutée")
            } else {
                t.value = value
                t.date = date
                Services.temperatures.update(t)
                Faces.info("Température modifiée")
            }
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            load()
        } catch (e: AlreadyExistsException) {
            Faces.error("Cette température existe déjà", e.message)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
    }

    fun delete(t: Temperature) {
        if (!login.require(login.isAdmin)) return
        try {
            Services.temperatures.getById(t.id!!)?.let { Services.temperatures.remove(it) }
            Faces.info("Température supprimée")
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
        }
        load()
    }

    /** Même logique que TemperatureResource.resolveSample */
    private fun sample(type: String): Temperature =
        if (type == "consigne") Consigne(0f, LocalDateTime.now()) else Mesurer(0f, LocalDateTime.now())
}
