package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.cheminLabel
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Named
import java.io.Serializable
import java.time.LocalDateTime
import java.util.Locale

/** Une carte du tableau de bord : une salle et la moyenne de ses boîtiers. */
data class SalleResume(
    val salle: Salle,
    val chemin: String,
    val moyenne: Float?,
    val derniereMesure: LocalDateTime?,
    val boitiers: Int,
    val consigne: Consigne?,
) : Serializable {
    private val ecart: Float? get() = consigne?.value?.let { c -> moyenne?.minus(c) }

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

    /** Écart à la consigne, ex. « +0,8° » (vide si pas de données). */
    val ecartLabel: String
        get() = ecart?.let { String.format(Locale.FRANCE, "%+.1f°", it) } ?: ""
}

@Named
@ViewScoped
class DashboardBean : Serializable {

    var resumes: List<SalleResume> = emptyList()

    /** Boxes that measure but belong to no salle: their readings appear nowhere until attached. */
    var boitiersNonRattaches: Int = 0

    @PostConstruct
    fun refresh() {
        // Exact type: étages, bâtiments and boîtiers are Salle subclasses but not salles.
        resumes = Services.salles.getAllByType(Salle(null))
            .sortedBy { it.name?.lowercase() }
            .map { salle ->
                val mesures = Services.climat.getMesures(salle)
                SalleResume(
                    salle = salle,
                    chemin = cheminLabel(salle),
                    moyenne = Services.climat.getMoyenne(salle),
                    derniereMesure = mesures.mapNotNull { it.mesure?.date }.maxOrNull(),
                    boitiers = mesures.size,
                    consigne = Services.climat.getConsigne(salle),
                )
            }
        boitiersNonRattaches = Services.hierarchie.getRacines().count { it is Boitier }
    }
}
