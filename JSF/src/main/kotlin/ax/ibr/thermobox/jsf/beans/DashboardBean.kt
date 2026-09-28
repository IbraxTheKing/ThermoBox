package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.jsf.SalleData
import ax.ibr.thermobox.jsf.Services
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Named
import java.io.Serializable

/** Une carte du tableau de bord. */
data class SalleResume(val salle: Salle, val mesure: Temperature?, val consigne: Temperature?) : Serializable {
    private val ecart: Float? get() = if (mesure != null && consigne != null) mesure.value - consigne.value else null

    val etat: String
        get() {
            val e = ecart ?: return "Pas de données"
            return when {
                e <= -0.5f -> "En chauffe"
                e >= 0.5f -> "Trop chaud"
                else -> "Stable"
            }
        }

    /** Sévérité du p:tag */
    val etatSeverity: String
        get() = when (etat) {
            "En chauffe" -> "warning"
            "Trop chaud" -> "danger"
            "Stable" -> "success"
            else -> "info"
        }
}

@Named
@ViewScoped
class DashboardBean : Serializable {

    var resumes: List<SalleResume> = emptyList()

    @PostConstruct
    fun refresh() {
        resumes = Services.salles.getAll().map { salle ->
            SalleResume(salle, SalleData.latestMesure(salle), SalleData.consigne(salle))
        }
    }
}
