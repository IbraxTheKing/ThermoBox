package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.basculerFavori as basculerFavoriEnBase
import ax.ibr.thermobox.jsf.favorisDe
import ax.ibr.thermobox.jsf.media.SalleImageServlet
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Sparkline
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.io.Serializable
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Où en est une salle par rapport à sa consigne ; [ton] choisit les couleurs (classe CSS tb-ton-…). */
enum class EtatSalle(val label: String, val ton: String, private val singulier: String, private val pluriel: String) {
    STABLE("Stable", "stable", "stable", "stables"),
    EN_CHAUFFE("En chauffe", "chauffe", "en chauffe", "en chauffe"),
    TROP_CHAUD("Trop chaud", "chaud", "trop chaude", "trop chaudes"),
    INCONNU("Pas de données", "inconnu", "sans données", "sans données");

    /** « 3 trop chaudes » */
    fun compte(n: Int): String = "$n ${if (n > 1) pluriel else singulier}"

    companion object {
        /** Within half a degree of the consigne counts as reached. */
        fun of(moyenne: Float?, consigne: Float?): EtatSalle {
            if (moyenne == null || consigne == null) return INCONNU
            val ecart = moyenne - consigne
            return when {
                ecart <= -0.5f -> EN_CHAUFFE
                ecart >= 0.5f -> TROP_CHAUD
                else -> STABLE
            }
        }
    }
}

/** Une carte du tableau de bord : une salle et la moyenne de ses boîtiers. */
data class SalleResume(
    val salle: Salle,
    val chemin: String,
    val batiment: Salle?,
    val moyenne: Float?,
    val derniereMesure: LocalDateTime?,
    val boitiers: Int,
    val consigne: Consigne?,
    /** Where the consigne comes from when the salle has none of its own (étage, bâtiment). */
    val consigneSource: Salle? = null,
    val favori: Boolean = false,
    /** Photo URL, relative to the application; null when the salle has none. */
    val image: String? = null,
    val tendance: Sparkline? = null,
) : Serializable {
    private val ecart: Float? get() = consigne?.value?.let { c -> moyenne?.minus(c) }

    val etat: EtatSalle get() = EtatSalle.of(moyenne, consigne?.value)

    val consigneTitre: String
        get() = consigneSource?.let { "Consigne héritée de « ${it.name} »" } ?: "Consigne de la salle"

    /** Écart à la consigne, ex. « +0,8° » (vide si pas de données). */
    val ecartLabel: String
        get() = ecart?.let { String.format(Locale.FRANCE, "%+.1f°", it) } ?: ""
}

/** Number of salles in a state, for the summary chips at the top of the dashboard. */
data class Compteur(val etat: EtatSalle, val nombre: Int) : Serializable {
    val texte: String get() = etat.compte(nombre)
}

/** Les salles d'un bâtiment (ou celles sans bâtiment quand [batiment] est null). */
data class GroupeBatiment(val batiment: Salle?, val moyenne: Float?, val salles: List<SalleResume>) : Serializable {
    val nom: String get() = batiment?.name ?: "Sans bâtiment"
}

@Named
@ViewScoped
class DashboardBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    // Filtres
    var recherche: String = ""
    /** Summary chip picked: only salles in that state are shown. */
    var etatFiltre: EtatSalle? = null
    var batimentId: Long? = null
    var batiments: List<Salle> = emptyList()

    // Affichage, recalculé par filtrer()
    var favoris: List<SalleResume> = emptyList()
    var groupes: List<GroupeBatiment> = emptyList()

    /** One chip per state that at least one salle is in. */
    var compteurs: List<Compteur> = emptyList()

    /** Boxes that measure but belong to no salle: their readings appear nowhere until attached. */
    var boitiersNonRattaches: Int = 0

    private var toutes: List<SalleResume> = emptyList()
    private var moyennesBatiments: Map<Long?, Float?> = emptyMap()

    // The curves read a day of measures per box: recomputed every few minutes, not on every poll.
    private var tendances: Map<Long, Sparkline?> = emptyMap()
    private var tendancesCalculees: LocalDateTime? = null

    val isVide: Boolean get() = toutes.isEmpty()
    val isAucunResultat: Boolean get() = toutes.isNotEmpty() && favoris.isEmpty() && groupes.isEmpty()

    /** Reloads from the database (page load, "Actualiser", p:poll); the filters are kept. */
    @PostConstruct
    fun refresh() {
        val favorisIds = favorisDe(login.currentUser)
        val images = Services.images.getDates()
        // Exact type: étages, bâtiments and boîtiers are Salle subclasses but not salles.
        val salles = Services.salles.getAllByType(Salle(null))
        val consignes = salles.associate { it.id to Services.climat.getConsigneEffective(it) }
        chargerTendances(salles, consignes.mapValues { it.value?.consigne })
        toutes = salles.map { salle ->
            val chemin = Services.hierarchie.getChemin(salle)
            val mesures = Services.climat.getMesures(salle)
            SalleResume(
                salle = salle,
                chemin = chemin.joinToString(" › ") { it.name.orEmpty() },
                batiment = chemin.firstOrNull { it is Batiment },
                moyenne = mesures.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                derniereMesure = mesures.mapNotNull { it.mesure?.date }.maxOrNull(),
                boitiers = mesures.size,
                consigne = consignes[salle.id]?.consigne,
                consigneSource = consignes[salle.id]?.takeIf { it.heritee }?.source,
                favori = salle.id in favorisIds,
                image = images[salle.id]?.let { SalleImageServlet.url(salle.id!!, it) },
                tendance = tendances[salle.id],
            )
        }
        batiments = Services.salles.getAllByType(Batiment(null)).sortedBy { it.name?.lowercase() }
        // Average of every box of the building, not of the salle averages (salles differ in size).
        moyennesBatiments = batiments.associate { it.id to Services.climat.getMoyenne(it) }
        boitiersNonRattaches = Services.hierarchie.getRacines().count { it is Boitier }
        compteurs = toutes.groupingBy { it.etat }.eachCount().toSortedMap().map { (etat, n) -> Compteur(etat, n) }
        filtrer()
    }

    /** Average curve of each salle over the last [PERIODE_TENDANCE]. */
    private fun chargerTendances(salles: List<Salle>, consignes: Map<Long?, Consigne?>) {
        val maintenant = LocalDateTime.now()
        val derniere = tendancesCalculees
        val connues = salles.all { it.id in tendances }
        if (connues && derniere != null && Duration.between(derniere, maintenant) < VALIDITE_TENDANCES) return

        val debut = maintenant.minus(PERIODE_TENDANCE)
        val zone = ZoneId.systemDefault()
        tendances = salles.filter { it.id != null }.associate { salle ->
            val historique = Services.climat.getMoyenneHistorique(
                salle, debut.atZone(zone).toInstant(), maintenant.atZone(zone).toInstant()
            )
            salle.id!! to Sparkline.of(historique, debut, maintenant, consignes[salle.id]?.value)
        }
        tendancesCalculees = maintenant
    }

    /** "Actualiser": unlike the poll, also redraws the curves. */
    fun actualiser() {
        tendancesCalculees = null
        refresh()
    }

    /** Applies the search and the building filter (no database access: runs on every keystroke). */
    fun filtrer() {
        val mots = normaliser(recherche).split(" ").filter { it.isNotBlank() }
        val visibles = toutes
            .filter { r -> batimentId == null || (if (batimentId == SANS_BATIMENT) r.batiment == null else r.batiment?.id == batimentId) }
            .filter { r -> normaliser("${r.salle.name} ${r.chemin}").let { texte -> mots.all { it in texte } } }
            .filter { r -> etatFiltre == null || r.etat == etatFiltre }
            .sortedBy { it.salle.name?.lowercase() }

        favoris = visibles.filter { it.favori }
        // A favourite is shown once, in its own section (view-transition names must be unique).
        groupes = visibles.filterNot { it.favori }
            .groupBy { it.batiment?.id }
            .map { (id, salles) -> GroupeBatiment(salles.first().batiment, moyennesBatiments[id], salles) }
            .sortedWith(compareBy({ it.batiment == null }, { it.nom.lowercase() }))
    }

    fun basculerFavori(salleId: Long) {
        val user = login.currentUser ?: return
        val salle = toutes.firstOrNull { it.salle.id == salleId }?.salle ?: return
        try {
            val favori = basculerFavoriEnBase(user, salle)
            toutes = toutes.map { if (it.salle.id == salleId) it.copy(favori = favori) else it }
            filtrer()
        } catch (e: Exception) {
            Faces.error("Impossible de modifier les favoris", e.message)
        }
    }

    /** Summary chip: shows only the salles in [etat]; a second click shows them all again. */
    fun basculerEtat(etat: EtatSalle) {
        etatFiltre = if (etatFiltre == etat) null else etat
        filtrer()
    }

    fun effacerFiltres() {
        recherche = ""
        batimentId = null
        etatFiltre = null
        filtrer()
    }

    /** Lower case without accents, so « etage » finds « Étage ». */
    private fun normaliser(texte: String): String =
        Normalizer.normalize(texte.lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(ACCENTS, "")

    companion object {
        /** Value of the "Sans bâtiment" filter entry (no real id is negative). */
        const val SANS_BATIMENT = -1L
        private val PERIODE_TENDANCE: Duration = Duration.ofHours(24)
        private val VALIDITE_TENDANCES: Duration = Duration.ofMinutes(5)
        private val ACCENTS = Regex("\\p{M}+")
    }
}
