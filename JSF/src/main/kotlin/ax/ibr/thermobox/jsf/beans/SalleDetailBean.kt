package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.MesureBoitier
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.basculerFavori as basculerFavoriEnBase
import ax.ibr.thermobox.jsf.favorisDe
import ax.ibr.thermobox.jsf.media.SalleImageServlet
import ax.ibr.thermobox.jsf.util.ChartBuilder
import ax.ibr.thermobox.jsf.util.ChartBuilder.Serie
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.event.FileUploadEvent
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
    /** Ancestors, from the building down: clickable breadcrumb. */
    var ancetres: List<Salle> = emptyList()
    var favori: Boolean = false
    /** Photo URL, relative to the application; null when there is none. */
    var image: String? = null
    var enfants: List<Salle> = emptyList()
    var moyenne: Float? = null
    var mesures: List<MesureBoitier> = emptyList()
    var consigne: Consigne? = null
    var nouvelleConsigne: Float? = null

    /** Shortcut in use (24, 168 or 720 h), null once dates were picked by hand. */
    var periodeHeures: Long? = 24

    var start: LocalDateTime = now().minusHours(24)
    var end: LocalDateTime = now()
    var historiqueConsignes: List<Consigne> = emptyList()
    var chartModel: LineChartModel = ChartBuilder.build(emptyList())

    val etat: EtatSalle get() = EtatSalle.of(moyenne, consigne?.value)

    val kindLabel: String get() = salle?.kind?.label.orEmpty()
    val isBoitier: Boolean get() = salle?.kind == SalleKind.BOITIER

    /** Only salles go on the dashboard, so only they can be favourites. */
    val isSalleSimple: Boolean get() = salle?.kind == SalleKind.SALLE

    /** Date of the most recent box reading, for the "Moyenne" card. */
    val derniereMesure: LocalDateTime? get() = mesures.mapNotNull { it.mesure?.date }.maxOrNull()

    /** f:viewAction (appelé une seule fois, pas sur les postbacks) */
    fun load() {
        salle = id?.let { runCatching { Services.salles.getById(it) }.getOrNull() }
        val s = salle ?: return
        ancetres = Services.hierarchie.getChemin(s)
        favori = s.id in favorisDe(login.currentUser)
        enfants = if (isBoitier) emptyList() else Services.hierarchie.getChildren(s)
        chargerImage()
        refresh()
        loadHistory()
    }

    /** Appelé aussi par le p:poll. */
    fun refresh() {
        val s = salle ?: return
        mesures = Services.climat.getMesures(s)
        moyenne = mesures.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        consigne = Services.climat.getConsigne(s)
        // The spinner starts from the current consigne rather than from its minimum.
        if (nouvelleConsigne == null) nouvelleConsigne = consigne?.value ?: CONSIGNE_PAR_DEFAUT
    }

    fun basculerFavori() {
        val s = salle ?: return
        val user = login.currentUser ?: return
        try {
            favori = basculerFavoriEnBase(user, s)
        } catch (e: Exception) {
            Faces.error("Impossible de modifier les favoris", e.message)
        }
    }

    /** p:fileUpload (ADMIN): the new photo replaces the previous one. */
    fun televerserImage(event: FileUploadEvent) {
        val s = salle ?: return
        if (!login.require(login.isAdmin)) return
        val fichier = event.file
        try {
            Services.images.add(SalleImage(s, fichier.contentType.orEmpty(), fichier.content))
            chargerImage()
            Faces.info("Photo enregistrée")
        } catch (e: IllegalArgumentException) {
            Faces.error("Photo refusée", e.message)
        } catch (e: Exception) {
            Faces.error("Impossible d'enregistrer la photo", e.message)
        }
    }

    fun retirerImage() {
        val s = salle ?: return
        if (!login.require(login.isAdmin)) return
        try {
            Services.images.getBySalle(s)?.let { Services.images.remove(it) }
            image = null
            Faces.info("Photo retirée")
        } catch (e: Exception) {
            Faces.error("Impossible de retirer la photo", e.message)
        }
    }

    private fun chargerImage() {
        val s = salle ?: return
        image = Services.images.getDates()[s.id]?.let { SalleImageServlet.url(s.id!!, it) }
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
        periodeHeures = heures
        end = now()
        start = end.minusHours(heures)
        loadHistory()
    }

    /** "Afficher", with the dates picked by hand. */
    fun afficher() {
        periodeHeures = null
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
                series.add(Serie("Moyenne de la salle", Services.climat.getMoyenneHistorique(s, from, to), "--tb-serie-moyenne"))
            }
            series.add(Serie("Consigne", consignes, "--tb-serie-consigne"))
            chartModel = ChartBuilder.build(series)
        } catch (e: Exception) {
            Faces.error("Impossible de charger l'historique", e.message)
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)

    private companion object {
        /** One colour per box, defined by the theme (light and dark differ). */
        val PALETTE = (1..8).map { "--tb-serie-$it" }
        const val CONSIGNE_PAR_DEFAUT = 20f
    }
}
