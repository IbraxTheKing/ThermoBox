package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import org.primefaces.model.DefaultTreeNode
import org.primefaces.model.TreeNode
import java.io.Serializable

/**
 * "Utilisateurs et groupes" tab of the Gestion page (ADMIN only): roles and accesses of several
 * users at once, and groups of users that give access to bâtiments, étages and salles.
 */
@Named
@ViewScoped
class DroitsBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    /** A user with what decides what they see. */
    data class LigneUser(
        val user: User,
        val illimite: Boolean,
        val elements: Int,
        val groupes: List<String>,
    ) : Serializable {
        val id: Long get() = user.id!!
        val acces: String
            get() = when {
                user.type == UserType.ADMIN -> "Tout (administrateur)"
                illimite -> "Tout ThermoBox"
                elements == 0 && groupes.isEmpty() -> "Aucun accès"
                else -> buildList {
                    if (elements > 0) add("$elements élément(s)")
                    if (groupes.isNotEmpty()) add("${groupes.size} groupe(s)")
                }.joinToString(" + ")
            }
        val sansAcces: Boolean get() = user.type != UserType.ADMIN && !illimite && elements == 0 && groupes.isEmpty()
    }

    /** A group as shown in its card. */
    data class LigneGroupe(val groupe: Groupe, val membres: List<String>, val elements: List<String>) : Serializable {
        val id: Long get() = groupe.id!!
    }

    val roles: List<UserType> = UserType.entries.toList()

    var users: List<LigneUser> = emptyList()
    var selection: List<LigneUser> = emptyList()
    var groupes: List<LigneGroupe> = emptyList()
    var recherche: String = ""

    // Actions on the selected users
    var roleGroupe: UserType? = null
    var groupeCibleId: Long? = null

    // Group dialog
    var editionId: Long? = null
    var formNom: String = ""
    var formDescription: String = ""
    var formMembres: List<Long> = emptyList()
    @Transient
    var formArbre: TreeNode<Salle> = DefaultTreeNode()
    var formSelection: Array<TreeNode<Salle>>? = null

    private var chargee = false

    val visibles: List<LigneUser>
        get() {
            val mot = recherche.trim().lowercase()
            return users.filter { mot.isEmpty() || it.user.username.orEmpty().lowercase().contains(mot) || it.groupes.any { g -> g.lowercase().contains(mot) } }
        }

    /** Read on first display of the tab (admins only). */
    fun chargerSiBesoin() {
        if (!chargee && login.isAdmin) charger()
    }

    fun charger() {
        if (!login.isAdmin) return
        chargee = true
        users = Services.users.getAll().sortedBy { it.username?.lowercase() }.map { u ->
            LigneUser(
                user = u,
                illimite = u.type == UserType.ADMIN || !Services.acces.estRestreint(u),
                elements = Services.acces.getAutorisations(u).size,
                groupes = Services.groupes.getGroupesDe(u).map { it.nom },
            )
        }
        groupes = Services.groupes.getAll().map { g ->
            val d = Services.groupes.detail(g)
            LigneGroupe(g, d.membres.mapNotNull { it.username }, d.elements.mapNotNull { it.name })
        }
        val ids = users.map { it.id }.toSet()
        selection = users.filter { l -> selection.any { it.id == l.id } && l.id in ids }
    }

    fun initiale(u: User): String = u.username?.trim()?.firstOrNull()?.uppercase().orEmpty()

    fun estMoi(u: User): Boolean = login.isOwner(u)

    // ---------------------------------------------------------------- one user

    /** Role chosen in the row's list. */
    fun changerRole(l: LigneUser, role: UserType) {
        appliquerRole(listOf(l), role)
    }

    // ---------------------------------------------------------------- several users

    fun appliquerRoleSelection() {
        val role = roleGroupe ?: run { Faces.warn("Choisis un rôle"); return }
        appliquerRole(selection, role)
        roleGroupe = null
    }

    private fun appliquerRole(cibles: List<LigneUser>, role: UserType) {
        if (!login.require(login.isAdmin)) return
        var n = 0
        val ignores = ArrayList<String>()
        cibles.forEach { l ->
            // Never lock oneself out of administration
            if (estMoi(l.user) && role != UserType.ADMIN) {
                ignores += "${l.user.username} (votre propre compte)"
                return@forEach
            }
            val u = Services.users.getById(l.id) ?: return@forEach
            if (u.type == role) return@forEach
            u.type = role
            Services.users.update(u)
            n++
        }
        if (n > 0) Faces.info("Rôle $role pour $n utilisateur(s)",
            if (role != UserType.ADMIN) "Un ancien administrateur n'a plus accès à rien tant que vous ne lui en donnez pas." else null)
        if (ignores.isNotEmpty()) Faces.warn("Non modifié", ignores.joinToString())
        charger()
    }

    /** Sees everything ([tout]) or only their accesses and groups. */
    fun accesSelection(tout: Boolean) {
        if (!login.require(login.isAdmin)) return
        var n = 0
        selection.filter { it.user.type != UserType.ADMIN }.forEach { l ->
            val u = Services.users.getById(l.id) ?: return@forEach
            Services.acces.definir(u, !tout, if (tout) emptyList() else Services.acces.getAutorisations(u))
            n++
        }
        Faces.info(if (tout) "$n utilisateur(s) voient tout ThermoBox" else "$n utilisateur(s) limités à leurs accès et groupes")
        charger()
    }

    fun ajouterAuGroupe() = membres(true)

    fun retirerDuGroupe() = membres(false)

    private fun membres(ajouter: Boolean) {
        if (!login.require(login.isAdmin)) return
        val g = groupeCibleId?.let { Services.groupes.getById(it) } ?: run { Faces.warn("Choisis un groupe"); return }
        val us = selection.map { it.user }
        if (ajouter) Services.groupes.ajouterMembres(g, us) else Services.groupes.retirerMembres(g, us)
        Faces.info(if (ajouter) "${us.size} utilisateur(s) ajouté(s) à « ${g.nom} »" else "${us.size} utilisateur(s) retiré(s) de « ${g.nom} »")
        charger()
    }

    // ---------------------------------------------------------------- groups

    fun nouveauGroupe() {
        editionId = null
        formNom = ""
        formDescription = ""
        formMembres = selection.map { it.id }
        construireArbre(emptySet())
    }

    fun modifierGroupe(l: LigneGroupe) {
        val g = Services.groupes.getById(l.id) ?: return
        val d = Services.groupes.detail(g)
        editionId = g.id
        formNom = g.nom
        formDescription = g.description.orEmpty()
        formMembres = d.membres.mapNotNull { it.id }
        construireArbre(d.elements.mapNotNull { it.id }.toSet())
    }

    fun enregistrerGroupe() {
        if (!login.require(login.isAdmin)) return
        val coches = formSelection.orEmpty().mapNotNull { it.data?.id }.toSet()
        val elements = retenir(formArbre, coches)
        val membres = formMembres.mapNotNull { Services.users.getById(it) }
        try {
            val g = Services.groupes.enregistrer(editionId?.let { Services.groupes.getById(it) }, formNom, formDescription, membres, elements)
            Faces.info("Groupe « ${g.nom} » enregistré", "${membres.size} membre(s), ${elements.size} élément(s) et leur contenu")
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            charger()
        } catch (e: IllegalArgumentException) {
            Faces.error("Groupe non enregistré", e.message)
        }
    }

    fun supprimerGroupe(l: LigneGroupe) {
        if (!login.require(login.isAdmin)) return
        Services.groupes.getById(l.id)?.let { Services.groupes.supprimer(it) }
        Faces.info("Groupe « ${l.groupe.nom} » supprimé", "Ses membres gardent leurs propres accès.")
        charger()
    }

    /** Bâtiments → étages → salles, ticked where the group gives access (no boxes: they follow their salle). */
    private fun construireArbre(accordes: Set<Long>) {
        val racine = DefaultTreeNode<Salle>()
        Services.hierarchie.getRacines().filter { it !is Boitier }.forEach { ajouter(it, racine, accordes, false) }
        formArbre = racine
        formSelection = null
    }

    private fun ajouter(salle: Salle, parent: TreeNode<Salle>, accordes: Set<Long>, herite: Boolean) {
        val noeud = DefaultTreeNode(salle, parent)
        val coche = herite || salle.id in accordes
        noeud.isSelected = coche
        noeud.isExpanded = salle.kind != SalleKind.SALLE
        Services.hierarchie.getChildren(salle).filter { it !is Boitier }.forEach { ajouter(it, noeud, accordes, coche) }
    }

    // Same rule as the Accès dialog: an element counts with all it contains only when fully ticked
    private fun retenir(noeud: TreeNode<Salle>, coches: Set<Long>): List<Salle> =
        noeud.children.flatMap { enfant -> if (complet(enfant, coches)) listOf(enfant.data) else retenir(enfant, coches) }

    private fun complet(noeud: TreeNode<Salle>, coches: Set<Long>): Boolean =
        noeud.data?.id in coches && noeud.children.all { complet(it, coches) }

    fun libelleType(salle: Salle): String = salle.kind.label
}
