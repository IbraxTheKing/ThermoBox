package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Unite
import jakarta.faces.context.FacesContext
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import java.io.Serializable
import java.text.Normalizer
import java.util.Locale

/**
 * Gestion page (GESTIONNAIRE and ADMIN): every bâtiment, étage, salle and box the user may see
 * in one table. A GESTIONNAIRE sets consignes, one by one or for a selection; an ADMIN also
 * creates, renames, moves and deletes, one by one or for a selection.
 */
@Named
@ViewScoped
class GestionBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** One row: an element with its place, temperature and consigne (in Celsius). */
    data class Ligne(
        val salle: Salle,
        val chemin: String,
        val parentId: Long?,
        val temperature: Float?,
        val consigne: Float?,
        /** The consigne comes from this ancestor (no consigne of its own). */
        val heriteeDe: String?,
        val boitiers: Int,
        val etat: EtatSalle,
    ) : Serializable {
        val id: Long get() = salle.id!!
        val kind: SalleKind get() = salle.kind
        val boitier: Boolean get() = salle is Boitier
        val rattache: Boolean get() = parentId != null || kind == SalleKind.BATIMENT
        val icone: String
            get() = when (kind) {
                SalleKind.BATIMENT -> "pi-building"
                SalleKind.ETAGE -> "pi-server"
                SalleKind.SALLE -> "pi-home"
                SalleKind.BOITIER -> "pi-wifi"
            }
    }

    var lignes: List<Ligne> = emptyList()
    var selection: List<Ligne> = emptyList()

    // Filters
    var recherche: String = ""
    /** null: every kind */
    var type: SalleKind? = null
    /** Only the elements off their consigne, or the boxes not attached. */
    var aTraiter: Boolean = false

    /** Consigne typed on each row, in the unit of the Préférences page (a map: EL gives no type, so any number). */
    var saisies: MutableMap<Long, Any?> = HashMap()

    // Selection actions
    var consigneGroupe: Float? = null
    var forcer: Boolean = false
    var cibleId: Long? = null

    // Create / edit dialog (ADMIN)
    var editionId: Long? = null
    var formKind: SalleKind = SalleKind.SALLE
    var formNom: String = ""
    var formParentId: Long? = null

    val types: List<SalleKind> get() = SalleKind.entries.filter { it != SalleKind.BOITIER || login.canSeeBoitiers }
    val admin: Boolean get() = login.isAdmin

    /** f:viewAction: GESTIONNAIRE and ADMIN only; the others go back to the dashboard. */
    fun verifier() {
        if (!login.isGestionnaire) {
            val ec = FacesContext.getCurrentInstance().externalContext
            ec.redirect(ec.requestContextPath + "/app/dashboard.xhtml")
            FacesContext.getCurrentInstance().responseComplete()
            return
        }
        if (lignes.isEmpty()) charger()
    }

    fun charger() {
        val acces = login.perimetre
        val toutes = Services.salles.getAll().filter { it.id != null && acces.peutVoir(it) }
            .filter { it !is Boitier || login.canSeeBoitiers }
        lignes = toutes.map { s ->
            val chemin = Services.hierarchie.getChemin(s)
            val effective = if (s is Boitier) null else Services.climat.getConsigneEffective(s)
            val mesures = Services.climat.getMesures(s)
            val temperature = if (s is Boitier) mesures.firstOrNull()?.mesure?.value else Services.climat.getMoyenne(s)
            Ligne(
                salle = s,
                chemin = chemin.joinToString(" › ") { it.name.orEmpty() },
                parentId = chemin.lastOrNull()?.id,
                temperature = temperature,
                consigne = effective?.consigne?.value,
                heriteeDe = effective?.takeIf { it.heritee }?.source?.name,
                boitiers = mesures.size,
                etat = EtatSalle.of(temperature, effective?.consigne?.value),
            )
        }.sortedWith(compareBy({ normaliser(it.chemin) }, { it.kind.ordinal }, { normaliser(it.salle.name.orEmpty()) }))
        // Keep the selection on what still exists
        val ids = lignes.map { it.id }.toSet()
        selection = lignes.filter { l -> selection.any { it.id == l.id } && l.id in ids }
    }

    /** Rows matching the filters. */
    val visibles: List<Ligne>
        get() {
            val mot = normaliser(recherche.trim())
            return lignes.filter { l ->
                (type == null || l.kind == type) &&
                    (mot.isEmpty() || normaliser("${l.salle.name} ${l.chemin}").contains(mot)) &&
                    (!aTraiter || l.etat == EtatSalle.EN_CHAUFFE || l.etat == EtatSalle.TROP_CHAUD || (l.boitier && !l.rattache))
            }
        }

    fun nombre(k: SalleKind?): Int = if (k == null) lignes.size else lignes.count { it.kind == k }
    val nombreATraiter: Int get() = lignes.count { it.etat == EtatSalle.EN_CHAUFFE || it.etat == EtatSalle.TROP_CHAUD || (it.boitier && !it.rattache) }

    fun filtrerType(k: SalleKind?) {
        type = if (type == k) null else k
    }

    // ---------------------------------------------------------------- consignes (GESTIONNAIRE)

    /** Consigne of one row, typed in its field. */
    fun appliquer(l: Ligne) {
        val saisie = nombre(saisies[l.id]) ?: run { Faces.warn("Indique une consigne pour « ${l.salle.name} »"); return }
        if (envoyer(listOf(l), saisie, false)) saisies.remove(l.id)
    }

    /** One consigne for the whole selection. */
    fun appliquerSelection() {
        val valeur = consigneGroupe ?: run { Faces.warn("Indique la consigne à envoyer"); return }
        if (envoyer(selection, valeur, forcer)) consigneGroupe = null
    }

    private fun envoyer(cibles: List<Ligne>, saisie: Float, forcer: Boolean): Boolean {
        if (!login.require(login.canSetConsigne)) return false
        val unite = Unite.courante()
        val celsius = Math.round(unite.versCelsius(saisie) * 10f) / 10f
        val elements = cibles.filter { !it.boitier && login.perimetre.peutVoir(it.salle) }
        if (elements.isEmpty()) {
            Faces.warn("Rien à régler", "Une consigne se règle sur un bâtiment, un étage ou une salle.")
            return false
        }
        val refusees = ArrayList<String>()
        var boitiers = 0
        for (l in elements) {
            try {
                boitiers += Services.climat.envoyerConsigne(l.salle, celsius, forcer).boitiers.size
            } catch (e: Exception) {
                refusees += "${l.salle.name} (${e.message})"
            }
        }
        val reussies = elements.size - refusees.size
        val texte = "${unite.texte(celsius)} ${unite.symbole}"
        if (reussies > 0) Faces.info("Consigne de $texte pour $reussies élément(s)", "Envoyée à $boitiers boîtier(s)")
        if (refusees.isNotEmpty()) Faces.error("Consigne refusée", refusees.joinToString())
        charger()
        return refusees.isEmpty()
    }

    // ---------------------------------------------------------------- structure (ADMIN)

    fun nouveau() {
        editionId = null
        formKind = type?.takeIf { it != SalleKind.BOITIER } ?: SalleKind.SALLE
        formNom = ""
        formParentId = selection.singleOrNull()?.takeIf { formKind.parentKinds.contains(it.kind) }?.id
    }

    fun modifier(l: Ligne) {
        editionId = l.id
        formKind = l.kind
        formNom = l.salle.name.orEmpty()
        formParentId = l.parentId
    }

    /** Where an element of [formKind] can go. */
    val parentsPossibles: List<Ligne> get() = lignes.filter { it.kind in formKind.parentKinds && it.id != editionId }

    /** Where the whole selection can go: allowed for every selected kind. */
    val ciblesPossibles: List<Ligne>
        get() {
            val kinds = selection.map { it.kind }.toSet()
            val ids = selection.map { it.id }.toSet()
            return lignes.filter { c -> c.id !in ids && kinds.all { k -> c.kind in k.parentKinds } }
        }

    fun libelle(l: Ligne): String = if (l.chemin.isEmpty()) "${l.salle.name} (${l.kind.label})" else "${l.salle.name} (${l.kind.label}, ${l.chemin})"

    fun enregistrer() {
        if (!login.require(login.isAdmin)) return
        val nom = formNom.trim()
        if (nom.isEmpty()) {
            Faces.error("Le nom est obligatoire")
            return
        }
        try {
            val salle = editionId?.let { Services.salles.getById(it) } ?: formKind.create(nom)
            salle.name = nom
            if (salle.id == null) Services.salles.add(salle) else Services.salles.update(salle)
            Services.hierarchie.setParent(salle, formParentId?.let { Services.salles.getById(it) })
            Faces.info("${salle.kind.label} « $nom » enregistré(e)")
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
        charger()
    }

    /** Moves the selection into [cibleId] (null: detaches it). */
    fun deplacerSelection() {
        if (!login.require(login.isAdmin)) return
        val cible = cibleId?.let { Services.salles.getById(it) }
        val refuses = ArrayList<String>()
        selection.forEach { l ->
            try {
                Services.hierarchie.setParent(l.salle, cible)
            } catch (e: Exception) {
                refuses += "${l.salle.name} (${e.message})"
            }
        }
        val ok = selection.size - refuses.size
        if (ok > 0) Faces.info(if (cible == null) "$ok élément(s) détaché(s)" else "$ok élément(s) déplacé(s) dans « ${cible.name} »")
        if (refuses.isNotEmpty()) Faces.error("Déplacement impossible", refuses.joinToString())
        cibleId = null
        charger()
    }

    fun supprimer(l: Ligne) = supprimerTous(listOf(l))

    fun supprimerSelection() = supprimerTous(selection)

    private fun supprimerTous(cibles: List<Ligne>) {
        if (!login.require(login.isAdmin)) return
        val refuses = ArrayList<String>()
        cibles.forEach { l ->
            try {
                Services.salles.getById(l.id)?.let { Services.salles.remove(it) }
            } catch (e: Exception) {
                refuses += "${l.salle.name} (${e.message})"
            }
        }
        val ok = cibles.size - refuses.size
        if (ok > 0) Faces.info("$ok élément(s) supprimé(s)")
        if (refuses.isNotEmpty()) Faces.error("Suppression impossible", refuses.joinToString())
        selection = emptyList()
        charger()
    }

    private fun nombre(v: Any?): Float? = when (v) {
        null -> null
        is Number -> v.toFloat()
        else -> v.toString().trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toFloatOrNull()
    }

    private fun normaliser(texte: String): String =
        Normalizer.normalize(texte.lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(ACCENTS, "")

    private companion object {
        val ACCENTS = Regex("\\p{Mn}+")
    }
}
