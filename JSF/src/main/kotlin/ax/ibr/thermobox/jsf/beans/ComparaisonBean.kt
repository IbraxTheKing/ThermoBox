package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Comparaison
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Unite
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.model.charts.ChartData
import org.primefaces.model.charts.line.LineChartDataSet
import org.primefaces.model.charts.line.LineChartModel
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Mesures page: every salle (or every bâtiment) on one chart over 24 h, 7 or 30 days, with a
 * table that compares them (now, minimum, maximum, average, time spent at the consigne).
 * Only what the user may see (AccesService).
 */
@Named
@ViewScoped
class ComparaisonBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** One curve: a salle or a bâtiment, with its figures over the period (in Celsius). */
    data class Ligne(
        val element: Room,
        val chemin: String,
        /** Colour of its curve: --tb-serie-<n> */
        val couleur: Int,
        val actuelle: Float?,
        val min: Float?,
        val max: Float?,
        val moyenne: Float?,
        val consigne: Float?,
        val dansLaCible: Float?,
        val etat: EtatSalle,
    ) : Serializable {
        val ecart: Float? get() = if (actuelle != null && consigne != null) actuelle - consigne else null
        /** « 92 % », « — » */
        val dansLaCibleTexte: String get() = dansLaCible?.let { "${Math.round(it * 100)} %" } ?: "—"
        val dansLaCiblePourcent: Int get() = dansLaCible?.let { Math.round(it * 100) } ?: 0
    }

    /** 24, 168 or 720 hours. */
    var periodeHeures: Long = 24
    /** "salle" or "batiment". */
    var regroupement: String = "salle"
    /** Bâtiment filter (salles only); null for all, -1 for the salles without bâtiment. */
    var batimentId: Long? = null

    var lignes: List<Ligne> = emptyList()
    var batiments: List<Room> = emptyList()
    var chartModel: LineChartModel = LineChartModel()

    val parBatiment: Boolean get() = regroupement == "batiment"
    val avecDonnees: List<Ligne> get() = lignes.filter { it.actuelle != null }
    val plusChaude: Ligne? get() = avecDonnees.maxByOrNull { it.actuelle!! }
    val plusFroide: Ligne? get() = avecDonnees.minByOrNull { it.actuelle!! }
    val moyenneGenerale: Float? get() = avecDonnees.takeIf { it.isNotEmpty() }?.map { it.actuelle!! }?.average()?.toFloat()
    val horsConsigne: Int get() = lignes.count { it.etat == EtatSalle.EN_CHAUFFE || it.etat == EtatSalle.TROP_CHAUD }

    @PostConstruct
    fun charger() {
        try {
            val acces = login.perimetre
            batiments = Services.salles.getAllByType(Building(null)).filter { acces.isVisible(it) }.sortedBy { it.name?.lowercase() }
            // Exact types: étages, bâtiments and boxes are Salle subclasses too
            val elements: List<Pair<Room, List<Room>>> = if (parBatiment) {
                // A bâtiment's curve aggregates all its boxes: only for a user who may see all of it
                Services.salles.getAllByType(Building(null)).filter { acces.canSee(it) }.map { it to emptyList() }
            } else {
                Services.salles.getAllByType(Room(null)).filter { acces.canSee(it) }
                    .map { it to Services.hierarchie.getPath(it) }
                    .filter { (_, chemin) ->
                        when (batimentId) {
                            null -> true
                            -1L -> chemin.none { it is Building }
                            else -> chemin.any { it is Building && it.id == batimentId }
                        }
                    }
            }.sortedWith(compareBy({ it.second.joinToString { s -> s.name.orEmpty() }.lowercase() }, { it.first.name?.lowercase() }))

            val fin = LocalDateTime.now().withSecond(0).withNano(0)
            val debut = fin.minusHours(periodeHeures)
            val pas = Comparaison.pas(debut, fin)
            val zone = ZoneId.systemDefault()
            val de = debut.atZone(zone).toInstant()
            val a = fin.atZone(zone).toInstant()

            val calculees = elements.mapIndexed { i, (element, chemin) ->
                val courbe = Comparaison.courbe(Services.climat.getAverageHistory(element, de, a), debut, fin, pas)
                val actuelle = Services.climat.getAverage(element)
                val consigne = Services.climat.getSetpoint(element)?.value
                Ligne(
                    element = element,
                    chemin = chemin.joinToString(" › ") { it.name.orEmpty() },
                    couleur = i % PALETTE + 1,
                    actuelle = actuelle,
                    min = courbe.min,
                    max = courbe.max,
                    moyenne = courbe.moyenne,
                    consigne = consigne,
                    dansLaCible = courbe.dansLaCible(consigne),
                    etat = EtatSalle.of(actuelle, consigne),
                ) to courbe
            }
            lignes = calculees.map { it.first }
            chartModel = modele(calculees, Comparaison.instants(debut, fin, pas))
        } catch (e: Exception) {
            Faces.error("Impossible de charger les courbes", e.message)
        }
    }

    private fun modele(calculees: List<Pair<Ligne, Comparaison.Courbe>>, instants: List<LocalDateTime>): LineChartModel {
        val unite = Unite.courante()
        val format = if (periodeHeures <= 24) HEURE else JOUR_HEURE
        val data = ChartData()
        data.labels = instants.map { format.format(it) }
        calculees.forEach { (ligne, courbe) ->
            data.addChartDataSet(LineChartDataSet().apply {
                label = ligne.element.name.orEmpty()
                borderColor = "--tb-serie-${ligne.couleur}"
                backgroundColor = borderColor
                fill = false
                isSpanGaps = true
                this.data = courbe.valeurs.map { v -> v?.let { Math.round(unite.depuisCelsius(it) * 10f) / 10f } }
            })
        }
        return LineChartModel().apply {
            this.data = data
            extender = "tbChartExtender"
        }
    }

    fun periode(heures: Long) {
        periodeHeures = heures
        charger()
    }

    fun regrouper(par: String) {
        regroupement = if (par == "batiment") "batiment" else "salle"
        charger()
    }

    private companion object {
        /** Colours --tb-serie-1 … --tb-serie-8 of the theme, then again. */
        const val PALETTE = 8
        val HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val JOUR_HEURE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    }
}
