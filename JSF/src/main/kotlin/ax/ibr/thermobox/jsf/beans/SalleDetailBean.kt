package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.jsf.SalleData
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
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

/** Page détail d'une salle : état courant, envoi de consigne, historique (≈ SalleTempAttrResource). */
@Named
@ViewScoped
class SalleDetailBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** f:viewParam "id" */
    var id: Long? = null

    var salle: Salle? = null
    var mesure: Temperature? = null
    var consigne: Consigne? = null
    var nouvelleConsigne: Float? = null

    var start: LocalDateTime = now().minusHours(24)
    var end: LocalDateTime = now()
    var historiqueConsignes: List<Consigne> = emptyList()
    var chartModel: LineChartModel = ChartBuilder.build(emptyList())

    /** f:viewAction (appelé une seule fois, pas sur les postbacks) */
    fun load() {
        salle = id?.let { runCatching { Services.salles.getById(it) }.getOrNull() }
        if (salle == null) return
        refresh()
        loadHistory()
    }

    /** Appelé aussi par le p:poll. */
    fun refresh() {
        val s = salle ?: return
        mesure = SalleData.latestMesure(s)
        consigne = SalleData.consigne(s)
    }

    /** PUT /api/salletemps/room/{id}/consigne?value=… */
    fun envoyerConsigne() {
        val s = salle ?: return
        val value = nouvelleConsigne ?: return
        if (!login.require(login.canSetConsigne)) return

        val c = Consigne(value, LocalDateTime.now())
        try {
            Services.salleTemps.add(SalleTempAttr(s, c))
        } catch (e: Exception) {
            Faces.error("Impossible d'enregistrer la consigne", e.message)
            return
        }
        try {
            Services.driver.sendConsigne(s, c)
            Faces.info("Consigne de %.1f °C envoyée".format(value))
        } catch (e: Exception) {
            Faces.warn("Consigne enregistrée mais non transmise au boîtier", e.message)
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
            val mesures = Services.salleTemps.getTemperaturesFromSalleTimed(s, from, to).orEmpty()
                .filterNot { it is Consigne }
            val consignes = Services.salleTemps.getConsignesFromSalleTimed(s, from, to).orEmpty()
            val moyennes = Services.salleTemps.getAverageTemperatureFromSalleTimed(s, from, to).orEmpty()

            historiqueConsignes = consignes.sortedByDescending { it.date }
            chartModel = ChartBuilder.build(
                listOf(
                    Serie("Température mesurée", mesures, "#e4572e"),
                    Serie("Consigne", consignes, "#2e86ab"),
                    Serie("Moyenne", moyennes, "#9ca3af"),
                )
            )
        } catch (e: Exception) {
            Faces.error("Impossible de charger l'historique", e.message)
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
}
