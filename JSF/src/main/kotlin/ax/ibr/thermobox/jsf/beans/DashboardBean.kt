package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.basculerFavori as basculerFavoriEnBase
import ax.ibr.thermobox.jsf.favorisDe
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.io.Serializable
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale

/** Une carte du tableau de bord : une salle et la moyenne de ses boîtiers. */
data class SalleResume(
    val salle: Salle,
    val chemin: String,
    val batiment: Salle?,
    val moyenne: Float?,
    val derniereMesure: LocalDateTime?,
    val boitiers: Int,
    val consigne: Consigne?,
    val favori: Boolean = false,
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
    var batimentId: Long? = null
    var batiments: List<Salle> = emptyList()

    // Affichage, recalculé par filtrer()
    var favoris: List<SalleResume> = emptyList()
    var groupes: List<GroupeBatiment> = emptyList()

    /** Boxes that measure but belong to no salle: their readings appear nowhere until attached. */
    var boitiersNonRattaches: Int = 0

    private var toutes: List<SalleResume> = emptyList()
    private var moyennesBatiments: Map<Long?, Float?> = emptyMap()

    val isVide: Boolean get() = toutes.isEmpty()
    val isAucunResultat: Boolean get() = toutes.isNotEmpty() && favoris.isEmpty() && groupes.isEmpty()

    /** Reloads from the database (page load, "Actualiser", p:poll); the filters are kept. */
    @PostConstruct
    fun refresh() {
        val favorisIds = favorisDe(login.currentUser)
        // Exact type: étages, bâtiments and boîtiers are Salle subclasses but not salles.
        toutes = Services.salles.getAllByType(Salle(null)).map { salle ->
            val chemin = Services.hierarchie.getChemin(salle)
            val mesures = Services.climat.getMesures(salle)
            SalleResume(
                salle = salle,
                chemin = chemin.joinToString(" › ") { it.name.orEmpty() },
                batiment = chemin.firstOrNull { it is Batiment },
                moyenne = mesures.mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                derniereMesure = mesures.mapNotNull { it.mesure?.date }.maxOrNull(),
                boitiers = mesures.size,
                consigne = Services.climat.getConsigne(salle),
                favori = salle.id in favorisIds,
            )
        }
        batiments = Services.salles.getAllByType(Batiment(null)).sortedBy { it.name?.lowercase() }
        // Average of every box of the building, not of the salle averages (salles differ in size).
        moyennesBatiments = batiments.associate { it.id to Services.climat.getMoyenne(it) }
        boitiersNonRattaches = Services.hierarchie.getRacines().count { it is Boitier }
        filtrer()
    }

    /** Applies the search and the building filter (no database access: runs on every keystroke). */
    fun filtrer() {
        val mots = normaliser(recherche).split(" ").filter { it.isNotBlank() }
        val visibles = toutes
            .filter { r -> batimentId == null || (if (batimentId == SANS_BATIMENT) r.batiment == null else r.batiment?.id == batimentId) }
            .filter { r -> normaliser("${r.salle.name} ${r.chemin}").let { texte -> mots.all { it in texte } } }
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

    fun effacerFiltres() {
        recherche = ""
        batimentId = null
        filtrer()
    }

    /** Lower case without accents, so « etage » finds « Étage ». */
    private fun normaliser(texte: String): String =
        Normalizer.normalize(texte.lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(ACCENTS, "")

    companion object {
        /** Value of the "Sans bâtiment" filter entry (no real id is negative). */
        const val SANS_BATIMENT = -1L
        private val ACCENTS = Regex("\\p{M}+")
    }
}
