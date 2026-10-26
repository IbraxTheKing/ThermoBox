package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.jsf.util.Libelles

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.business.implementations.ClimateServiceImpl
import ax.ibr.thermobox.common.services.EffectiveSetpoint
import ax.ibr.thermobox.common.services.DeviceMeasurement
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.basculerFavori as basculerFavoriEnBase
import ax.ibr.thermobox.jsf.favorisDe
import ax.ibr.thermobox.jsf.media.SalleImageServlet
import ax.ibr.thermobox.jsf.util.ChartBuilder
import ax.ibr.thermobox.jsf.util.ChartBuilder.Serie
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Unite
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.event.FileUploadEvent
import org.primefaces.model.charts.line.LineChartModel
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneId

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

    var salle: Room? = null
    /** Ancestors, from the building down: clickable breadcrumb. */
    var ancetres: List<Room> = emptyList()
    var favori: Boolean = false
    /** Photo URL, relative to the application; null when there is none. */
    var image: String? = null
    var enfants: List<Room> = emptyList()
    var moyenne: Float? = null
    var mesures: List<DeviceMeasurement> = emptyList()
    var consigneEffective: EffectiveSetpoint? = null
    var nouvelleConsigne: Float? = null

    /** Étages / salles below with their own consigne: the new one leaves them alone unless [forcer]. */
    var consignesPropres: List<EffectiveSetpoint> = emptyList()
    var forcer: Boolean = false

    /** Étage or bâtiment: the salles it contains, at any depth. */
    var salles: List<SalleResume> = emptyList()

    /** Shortcut in use (24, 168 or 720 h), null once dates were picked by hand. */
    var periodeHeures: Long? = 24

    var start: LocalDateTime = now().minusHours(24)
    var end: LocalDateTime = now()
    var historiqueConsignes: List<Setpoint> = emptyList()
    var chartModel: LineChartModel = ChartBuilder.build(emptyList())

    val consigne: Setpoint? get() = consigneEffective?.setpoint

    val etat: EtatSalle get() = EtatSalle.of(moyenne, consigne?.value)

    val isConteneur: Boolean get() = salle?.kind == RoomKind.FLOOR || salle?.kind == RoomKind.BUILDING

    val kindLabel: String get() = salle?.kind?.let { Libelles.kind(it) }.orEmpty()
    val isBoitier: Boolean get() = salle?.kind == RoomKind.DEVICE

    /** Breadcrumb: an ancestor outside the user's accesses is shown as plain text, not as a link. */
    fun accessible(s: Room): Boolean = login.perimetre.canSee(s)

    /** Only salles go on the dashboard, so only they can be favourites. */
    val isSalleSimple: Boolean get() = salle?.kind == RoomKind.ROOM

    /** Date of the most recent box reading, for the "Moyenne" card. */
    val derniereMesure: LocalDateTime? get() = mesures.mapNotNull { it.measurement?.date }.maxOrNull()

    /** f:viewAction (appelé une seule fois, pas sur les postbacks) */
    fun load() {
        salle = id?.let { runCatching { Services.salles.getById(it) }.getOrNull() }
            // A VIEWER gets the same "not found" page as for an unknown id, and so does anyone
            // for an element outside their accesses (even typed by hand in the URL).
            ?.takeUnless { it.kind == RoomKind.DEVICE && !login.canSeeBoitiers }
            ?.takeIf { login.perimetre.canSee(it) }
        val s = salle ?: return
        ancetres = Services.hierarchie.getPath(s)
        favori = s.id in favorisDe(login.currentUser)
        enfants = if (isBoitier) emptyList()
        else Services.hierarchie.getChildren(s).filter { login.canSeeBoitiers || it.kind != RoomKind.DEVICE }
        chargerImage()
        refresh()
        loadHistory()
    }

    /** Appelé aussi par le p:poll. */
    fun refresh() {
        val s = salle ?: return
        // Accesses withdrawn while the page was open: nothing more is shown.
        if (!login.perimetre.canSee(s)) {
            salle = null
            Faces.warn("Vous n'avez plus accès à cet élément")
            return
        }
        mesures = Services.climat.getMeasurements(s)
        moyenne = mesures.mapNotNull { it.measurement?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        consigneEffective = Services.climat.getEffectiveSetpoint(s)
        consignesPropres = if (isBoitier) emptyList() else Services.climat.getOwnSetpoints(s)
        if (isConteneur) salles = Services.hierarchie.getRooms(s).map { resumer(it) }
        // The spinner starts from the current consigne rather than from its minimum.
        // Shown in the unit of the Préférences page (rounded to the step of the field)
        if (nouvelleConsigne == null) nouvelleConsigne = (consigne?.value ?: CONSIGNE_PAR_DEFAUT).let { c ->
            val u = Unite.courante()
            if (u == Unite.FAHRENHEIT) Math.round(u.depuisCelsius(c)).toFloat() else c
        }
    }

    fun basculerFavori() {
        val s = salle ?: return
        if (!login.require(login.perimetre.canSee(s))) return
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
            Services.images.add(RoomImage(s, fichier.contentType.orEmpty(), fichier.content))
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
            Services.images.getByRoom(s)?.let { Services.images.remove(it) }
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

    private fun resumer(salle: Room): SalleResume {
        val mesuresSalle = Services.climat.getMeasurements(salle)
        val effective = Services.climat.getEffectiveSetpoint(salle)
        return SalleResume(
            salle = salle,
            chemin = "",
            batiment = null,
            moyenne = mesuresSalle.mapNotNull { it.measurement?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            derniereMesure = mesuresSalle.mapNotNull { it.measurement?.date }.maxOrNull(),
            boitiers = mesuresSalle.size,
            consigne = effective?.setpoint,
            consigneSource = effective?.takeIf { it.inherited }?.source,
        )
    }

    /** « Étage 1 (19,0 °C), Everest (22,5 °C) » */
    val consignesPropresLabel: String
        get() = Unite.courante().let { u -> consignesPropres.joinToString { "${it.source.name} (${u.texte(it.setpoint.value)} ${u.symbole})" } }

    /** Écart d'un boîtier à la moyenne de la salle, ex. « +0,4° ». */
    fun ecart(m: DeviceMeasurement): String {
        val valeur = m.measurement?.value ?: return ""
        val moy = moyenne ?: return ""
        return Unite.courante().ecart(valeur - moy)
    }

    /**
     * Saves the consigne on the element; what it contains follows it, except the étages / salles
     * that have their own consigne, unless [forcer] is on.
     */
    fun envoyerConsigne() {
        val s = salle ?: return
        val unite = Unite.courante()
        // Entered in the unit of the Préférences page, stored in Celsius
        val saisie = nouvelleConsigne ?: return
        val value = Math.round(unite.versCelsius(saisie) * 10f) / 10f
        if (!login.require(login.canSetConsigne && login.perimetre.canSee(s))) return

        val envoi = try {
            Services.climat.sendSetpoint(s, value, forcer)
        } catch (e: Exception) {
            Faces.error("Consigne refusée", e.message)
            return
        }
        val titre = "Consigne de ${unite.texte(value)} ${unite.symbole} enregistrée"
        val details = buildList {
            add(if (envoi.devices.isEmpty()) "aucun boîtier à prévenir" else "envoyée à ${envoi.devices.size} boîtier(s)")
            if (envoi.replaced.isNotEmpty()) add("remplace celle de ${envoi.replaced.joinToString { it.name.orEmpty() }}")
            if (envoi.kept.isNotEmpty()) add("${envoi.kept.joinToString { it.source.name.orEmpty() }} garde(nt) sa consigne")
        }.joinToString(" ; ").replaceFirstChar { it.uppercase() }
        if (envoi.failures.isEmpty()) Faces.info(titre, details)
        else Faces.warn(titre, "$details. Non transmise à : ${envoi.failures.joinToString { it.name.orEmpty() }}")

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
            val historiques = Services.climat.getHistories(s, from, to)
            val consignes = Services.climat.getSetpoints(s, from, to)
            historiqueConsignes = consignes.sortedByDescending { it.date }

            val series = ArrayList<Serie>()
            if (isConteneur) {
                // One curve per salle (average of its boxes) rather than one per box.
                val parSalle = Services.hierarchie.getRooms(s).map { salle ->
                    val boitiers = Services.hierarchie.getDevices(salle).map { it.id }.toSet()
                    salle to historiques.filter { it.device.id in boitiers }
                }.filter { (_, h) -> h.isNotEmpty() }
                parSalle.forEachIndexed { i, (salle, h) ->
                    series.add(Serie(salle.name.orEmpty(), ClimateServiceImpl.movingAverage(h), PALETTE[i % PALETTE.size]))
                }
                if (parSalle.size > 1) {
                    series.add(Serie(if (s.kind == RoomKind.FLOOR) "Moyenne de l'étage" else "Moyenne du bâtiment", ClimateServiceImpl.movingAverage(historiques), "--tb-serie-moyenne"))
                }
            } else if (!login.canSeeBoitiers) {
                // VIEWER: the salle's temperature only, not one curve per box.
                if (historiques.isNotEmpty()) {
                    series.add(Serie("Température", ClimateServiceImpl.movingAverage(historiques), "--tb-serie-moyenne"))
                }
            } else {
                historiques.forEachIndexed { i, h ->
                    series.add(Serie(h.device.name.orEmpty(), h.measurements, PALETTE[i % PALETTE.size]))
                }
                // A single box is its own average: no need for a duplicate curve.
                if (historiques.size > 1) {
                    series.add(Serie("Moyenne de la salle", ClimateServiceImpl.movingAverage(historiques), "--tb-serie-moyenne"))
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
