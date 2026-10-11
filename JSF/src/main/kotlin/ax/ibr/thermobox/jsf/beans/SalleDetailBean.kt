package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.MesureBoitier
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.cheminLabel
import ax.ibr.thermobox.jsf.util.ChartBuilder
import ax.ibr.thermobox.jsf.util.ChartBuilder.Serie
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.model.charts.line.LineChartModel
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Page détail d'un élément (en pratique une salle) : moyenne de ses boîtiers, température de
 * chaque boîtier, envoi de consigne à tous les boîtiers, historique.
 */
@Named
@ViewScoped
class SalleDetailBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** f:viewParam "id" */
    var id: Long? = null

    var salle: Salle? = null
    var chemin: String = ""
    var enfants: List<Salle> = emptyList()
    var moyenne: Float? = null
    var mesures: List<MesureBoitier> = emptyList()
    var consigne: Consigne? = null
    var nouvelleConsigne: Float? = null

    var start: LocalDateTime = now().minusHours(24)
    var end: LocalDateTime = now()
    var historiqueConsignes: List<Consigne> = emptyList()
    var chartModel: LineChartModel = ChartBuilder.build(emptyList())

    val kindLabel: String get() = salle?.kind?.label.orEmpty()
    val isBoitier: Boolean get() = salle?.kind == SalleKind.BOITIER

    /** Date of the most recent box reading, for the "Moyenne" card. */
    val derniereMesure: LocalDateTime? get() = mesures.mapNotNull { it.mesure?.date }.maxOrNull()

    /** f:viewAction (appelé une seule fois, pas sur les postbacks) */
    fun load() {
        salle = id?.let { runCatching { Services.salles.getById(it) }.getOrNull() }
        val s = salle ?: return
        chemin = cheminLabel(s)
        enfants = if (isBoitier) emptyList() else Services.hierarchie.getChildren(s)
        refresh()
        loadHistory()
    }

    /** Appelé aussi par le p:poll. */
    fun refresh() {
        val s = salle ?: return
        mesures = Services.climat.getMesures(s)
        moyenne = mesures.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        consigne = Services.climat.getConsigne(s)
    }

    /** Écart d'un boîtier à la moyenne de la salle, ex. « +0,4° ». */
    fun ecart(m: MesureBoitier): String {
        val valeur = m.mesure?.value ?: return ""
        val moy = moyenne ?: return ""
        return String.format(Locale.FRANCE, "%+.1f°", valeur - moy)
    }

    /** Enregistre la consigne sur la salle et l'envoie à chacun de ses boîtiers. */
    fun envoyerConsigne() {
        val s = salle ?: return
        val value = nouvelleConsigne ?: return
        if (!login.require(login.canSetConsigne)) return

        val echecs = try {
            Services.climat.envoyerConsigne(s, value)
        } catch (e: Exception) {
            Faces.error("Consigne refusée", e.message)
            return
        }
        when {
            mesures.isEmpty() -> Faces.warn("Consigne enregistrée", "Aucun boîtier dans cette salle pour l'appliquer.")
            echecs.isEmpty() -> Faces.info("Consigne de %.1f °C envoyée à %d boîtier(s)".format(value, mesures.size))
            else -> Faces.warn(
                "Consigne enregistrée mais non transmise à certains boîtiers",
                echecs.joinToString { it.name.orEmpty() }
            )
        }
        nouvelleConsigne = null
        refresh()
        loadHistory()
    }

    /** Raccourcis 24 h / 7 j / 30 j */
    fun periode(heures: Long) {
        end = now()
        start = end.minusHours(heures)
        loadHistory()
    }

    fun loadHistory() {
        val s = salle ?: return
        if (!start.isBefore(end)) {
            Faces.error("La date de début doit précéder la date de fin")
            return
        }
        val zone = ZoneId.systemDefault()
        val from = start.atZone(zone).toInstant()
        val to = end.atZone(zone).toInstant()

        try {
            val historiques = Services.climat.getHistoriques(s, from, to)
            val consignes = Services.climat.getConsignes(s, from, to)
            historiqueConsignes = consignes.sortedByDescending { it.date }

            val series = ArrayList<Serie>()
            historiques.forEachIndexed { i, h ->
                series.add(Serie(h.boitier.name.orEmpty(), h.mesures, PALETTE[i % PALETTE.size]))
            }
            // A single box is its own average: no need for a duplicate curve.
            if (historiques.size > 1) {
                series.add(Serie("Moyenne de la salle", Services.climat.getMoyenneHistorique(s, from, to), "#ffffff"))
            }
            series.add(Serie("Consigne", consignes, "#2e86ab"))
            chartModel = ChartBuilder.build(series)
        } catch (e: Exception) {
            Faces.error("Impossible de charger l'historique", e.message)
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)

    private companion object {
        /** One colour per box; the average is white and the consigne blue. */
        val PALETTE = listOf("#e4572e", "#ffd36a", "#71e6aa", "#c792ea", "#ff8d91", "#8cecf2", "#f4a261", "#9ca3af")
    }
}
