package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.jsf.util.Libelles

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
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
    data class LigneGroupe(val groupe: UserGroup, val membres: List<String>, val elements: List<String>) : Serializable {
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
    var formArbre: TreeNode<Room> = DefaultTreeNode()
    var formSelection: Array<TreeNode<Room>>? = null

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
                illimite = u.type == UserType.ADMIN || !Services.acces.isRestricted(u),
                elements = Services.acces.getGrants(u).size,
                groupes = Services.groupes.getGroupsOf(u).map { it.name },
            )
        }
        groupes = Services.groupes.getAll().map { g ->
            val d = Services.groupes.detail(g)
            LigneGroupe(g, d.members.mapNotNull { it.username }, d.elements.mapNotNull { it.name })
        }
        val ids = users.map { it.id }.toSet()
        selection = users.filter { l -> selection.any { it.id == l.id } && l.id in ids }
    }

    fun initiale(u: User): String = u.username?.trim()?.firstOrNull()?.uppercase().orEmpty()

    fun estMoi(u: User): Boolean = login.isOwner(u)

    /** × of the bottom bar. */
    fun toutDecocher() {
        selection = emptyList()
    }

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
            Services.acces.define(u, !tout, if (tout) emptyList() else Services.acces.getGrants(u))
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
        if (ajouter) Services.groupes.addMembers(g, us) else Services.groupes.removeMembers(g, us)
        Faces.info(if (ajouter) "${us.size} utilisateur(s) ajouté(s) à « ${g.name} »" else "${us.size} utilisateur(s) retiré(s) de « ${g.name} »")
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
        formNom = g.name
        formDescription = g.description.orEmpty()
        formMembres = d.members.mapNotNull { it.id }
        construireArbre(d.elements.mapNotNull { it.id }.toSet())
    }

    fun enregistrerGroupe() {
        if (!login.require(login.isAdmin)) return
        val coches = formSelection.orEmpty().mapNotNull { it.data?.id }.toSet()
        val elements = retenir(formArbre, coches)
        val membres = formMembres.mapNotNull { Services.users.getById(it) }
        try {
            val g = Services.groupes.save(editionId?.let { Services.groupes.getById(it) }, formNom, formDescription, membres, elements)
            Faces.info("Groupe « ${g.name} » enregistré", "${membres.size} membre(s), ${elements.size} élément(s) et leur contenu")
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            charger()
        } catch (e: IllegalArgumentException) {
            Faces.error("Groupe non enregistré", e.message)
        }
    }

    fun supprimerGroupe(l: LigneGroupe) {
        if (!login.require(login.isAdmin)) return
        Services.groupes.getById(l.id)?.let { Services.groupes.deleteGroup(it) }
        Faces.info("Groupe « ${l.groupe.name} » supprimé", "Ses membres gardent leurs propres accès.")
        charger()
    }

    /** Bâtiments → étages → salles, ticked where the group gives access (no boxes: they follow their salle). */
    private fun construireArbre(accordes: Set<Long>) {
        val racine = DefaultTreeNode<Room>()
        Services.hierarchie.getRoots().filter { it !is Device }.forEach { ajouter(it, racine, accordes, false) }
        formArbre = racine
        formSelection = null
    }

    private fun ajouter(salle: Room, parent: TreeNode<Room>, accordes: Set<Long>, herite: Boolean) {
        val noeud = DefaultTreeNode(salle, parent)
        val coche = herite || salle.id in accordes
        noeud.isSelected = coche
        noeud.isExpanded = salle.kind != RoomKind.ROOM
        Services.hierarchie.getChildren(salle).filter { it !is Device }.forEach { ajouter(it, noeud, accordes, coche) }
    }

    // Same rule as the Accès dialog: an element counts with all it contains only when fully ticked
    private fun retenir(noeud: TreeNode<Room>, coches: Set<Long>): List<Room> =
        noeud.children.flatMap { child -> if (complet(child, coches)) listOf(child.data) else retenir(child, coches) }

    private fun complet(noeud: TreeNode<Room>, coches: Set<Long>): Boolean =
        noeud.data?.id in coches && noeud.children.all { complet(it, coches) }

    fun libelleType(salle: Room): String = Libelles.kind(salle.kind)
}
