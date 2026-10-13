package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.business.implementations.ClimatServiceImpl
import ax.ibr.thermobox.common.services.ConsigneEffective
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
 * Page détail d'un élément : moyenne de ses boîtiers, consigne, historique. Une salle montre
 * ses boîtiers ; un étage ou un bâtiment montre ses salles (tuiles et courbes).
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
    var consigneEffective: ConsigneEffective? = null
    var nouvelleConsigne: Float? = null

    /** Étages / salles below with their own consigne: the new one leaves them alone unless [forcer]. */
    var consignesPropres: List<ConsigneEffective> = emptyList()
    var forcer: Boolean = false

    /** Étage or bâtiment: the salles it contains, at any depth. */
    var salles: List<SalleResume> = emptyList()

    /** Shortcut in use (24, 168 or 720 h), null once dates were picked by hand. */
    var periodeHeures: Long? = 24

    var start: LocalDateTime = now().minusHours(24)
    var end: LocalDateTime = now()
    var historiqueConsignes: List<Consigne> = emptyList()
    var chartModel: LineChartModel = ChartBuilder.build(emptyList())

    val consigne: Consigne? get() = consigneEffective?.consigne

    val etat: EtatSalle get() = EtatSalle.of(moyenne, consigne?.value)

    val isConteneur: Boolean get() = salle?.kind == SalleKind.ETAGE || salle?.kind == SalleKind.BATIMENT

    val kindLabel: String get() = salle?.kind?.label.orEmpty()
    val isBoitier: Boolean get() = salle?.kind == SalleKind.BOITIER

    /** Only salles go on the dashboard, so only they can be favourites. */
    val isSalleSimple: Boolean get() = salle?.kind == SalleKind.SALLE

    /** Date of the most recent box reading, for the "Moyenne" card. */
    val derniereMesure: LocalDateTime? get() = mesures.mapNotNull { it.mesure?.date }.maxOrNull()

    /** f:viewAction (appelé une seule fois, pas sur les postbacks) */
    fun load() {
        salle = id?.let { runCatching { Services.salles.getById(it) }.getOrNull() }
            // A VIEWER gets the same "not found" page as for an unknown id.
            ?.takeUnless { it.kind == SalleKind.BOITIER && !login.canSeeBoitiers }
        val s = salle ?: return
        ancetres = Services.hierarchie.getChemin(s)
        favori = s.id in favorisDe(login.currentUser)
        enfants = if (isBoitier) emptyList()
        else Services.hierarchie.getChildren(s).filter { login.canSeeBoitiers || it.kind != SalleKind.BOITIER }
        chargerImage()
        refresh()
        loadHistory()
    }

    /** Appelé aussi par le p:poll. */
    fun refresh() {
        val s = salle ?: return
        mesures = Services.climat.getMesures(s)
        moyenne = mesures.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        consigneEffective = Services.climat.getConsigneEffective(s)
        consignesPropres = if (isBoitier) emptyList() else Services.climat.getConsignesPropres(s)
        if (isConteneur) salles = Services.hierarchie.getSalles(s).map { resumer(it) }
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

    private fun resumer(salle: Salle): SalleResume {
        val mesuresSalle = Services.climat.getMesures(salle)
        val effective = Services.climat.getConsigneEffective(salle)
        return SalleResume(
            salle = salle,
            chemin = "",
            batiment = null,
            moyenne = mesuresSalle.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            derniereMesure = mesuresSalle.mapNotNull { it.mesure?.date }.maxOrNull(),
            boitiers = mesuresSalle.size,
            consigne = effective?.consigne,
            consigneSource = effective?.takeIf { it.heritee }?.source,
        )
    }

    /** « Étage 1 (19,0 °C), Everest (22,5 °C) » */
    val consignesPropresLabel: String
        get() = consignesPropres.joinToString { String.format(Locale.FRANCE, "%s (%.1f °C)", it.source.name, it.consigne.value) }

    /** Écart d'un boîtier à la moyenne de la salle, ex. « +0,4° ». */
    fun ecart(m: MesureBoitier): String {
        val valeur = m.mesure?.value ?: return ""
        val moy = moyenne ?: return ""
        return String.format(Locale.FRANCE, "%+.1f°", valeur - moy)
    }

    /**
     * Saves the consigne on the element; what it contains follows it, except the étages / salles
     * that have their own consigne, unless [forcer] is on.
     */
    fun envoyerConsigne() {
        val s = salle ?: return
        val value = nouvelleConsigne ?: return
        if (!login.require(login.canSetConsigne)) return

        val envoi = try {
            Services.climat.envoyerConsigne(s, value, forcer)
        } catch (e: Exception) {
            Faces.error("Consigne refusée", e.message)
            return
        }
        val titre = String.format(Locale.FRANCE, "Consigne de %.1f °C enregistrée", value)
        val details = buildList {
            add(if (envoi.boitiers.isEmpty()) "aucun boîtier à prévenir" else "envoyée à ${envoi.boitiers.size} boîtier(s)")
            if (envoi.remplacees.isNotEmpty()) add("remplace celle de ${envoi.remplacees.joinToString { it.name.orEmpty() }}")
            if (envoi.conservees.isNotEmpty()) add("${envoi.conservees.joinToString { it.source.name.orEmpty() }} garde(nt) sa consigne")
        }.joinToString(" ; ").replaceFirstChar { it.uppercase() }
        if (envoi.echecs.isEmpty()) Faces.info(titre, details)
        else Faces.warn(titre, "$details. Non transmise à : ${envoi.echecs.joinToString { it.name.orEmpty() }}")

        nouvelleConsigne = null
        forcer = false
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
            if (isConteneur) {
                // One curve per salle (average of its boxes) rather than one per box.
                val parSalle = Services.hierarchie.getSalles(s).map { salle ->
                    val boitiers = Services.hierarchie.getBoitiers(salle).map { it.id }.toSet()
                    salle to historiques.filter { it.boitier.id in boitiers }
                }.filter { (_, h) -> h.isNotEmpty() }
                parSalle.forEachIndexed { i, (salle, h) ->
                    series.add(Serie(salle.name.orEmpty(), ClimatServiceImpl.moyenneGlissante(h), PALETTE[i % PALETTE.size]))
                }
                if (parSalle.size > 1) {
                    series.add(Serie(if (s.kind == SalleKind.ETAGE) "Moyenne de l'étage" else "Moyenne du bâtiment", ClimatServiceImpl.moyenneGlissante(historiques), "--tb-serie-moyenne"))
                }
            } else if (!login.canSeeBoitiers) {
                // VIEWER: the salle's temperature only, not one curve per box.
                if (historiques.isNotEmpty()) {
                    series.add(Serie("Température", ClimatServiceImpl.moyenneGlissante(historiques), "--tb-serie-moyenne"))
                }
            } else {
                historiques.forEachIndexed { i, h ->
                    series.add(Serie(h.boitier.name.orEmpty(), h.mesures, PALETTE[i % PALETTE.size]))
                }
                // A single box is its own average: no need for a duplicate curve.
                if (historiques.size > 1) {
                    series.add(Serie("Moyenne de la salle", ClimatServiceImpl.moyenneGlissante(historiques), "--tb-serie-moyenne"))
                }
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
