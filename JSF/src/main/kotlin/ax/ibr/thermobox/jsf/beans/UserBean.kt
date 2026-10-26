package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.jsf.util.Libelles

import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.usernameTaken
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.isNew
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.utils.exceptions.AlreadyExistsException
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import org.primefaces.model.DefaultTreeNode
import org.primefaces.model.TreeNode
import java.io.Serializable

/** Gestion des utilisateurs (ADMIN), équivalent de UserResource. */
@Named
@ViewScoped
class UserBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    val roles: List<UserType> = UserType.entries.toList()

    var users: List<User> = emptyList()
    var selected: User? = null
    var newPassword: String = ""

    val isCreating: Boolean get() = isNew(selected?.id)

    var recherche: String = ""
    /** Role chip picked, null for all. */
    var roleFiltre: UserType? = null

    /** Users matching the search and the role chip, by name. */
    val visibles: List<User>
        get() {
            val mot = recherche.trim().lowercase()
            return users
                .filter { mot.isEmpty() || it.username.orEmpty().lowercase().contains(mot) }
                .filter { roleFiltre == null || it.type == roleFiltre }
                .sortedBy { it.username?.lowercase() }
        }

    fun nombre(role: UserType): Int = users.count { it.type == role }

    fun basculerRole(role: UserType) {
        roleFiltre = if (roleFiltre == role) null else role
    }

    fun initiale(user: User): String = user.username?.trim()?.firstOrNull()?.uppercase().orEmpty()

    // ------------------------------------------------------------- accesses (dialog "Accès")

    /** User whose accesses are being edited. */
    var accesUser: User? = null
    /** false: sees everything; true: only what is ticked in [accesArbre]. */
    var accesRestreint: Boolean = true
    /** Bâtiments → étages → salles (no boxes: they follow their salle). */
    @Transient
    var accesArbre: TreeNode<Room> = DefaultTreeNode()
    var accesSelection: Array<TreeNode<Room>>? = null

    private var resumes: Map<Long?, String> = emptyMap()

    @PostConstruct
    fun load() {
        users = Services.users.getAll()
        resumes = users.associate { it.id to resumer(it) }
    }

    /** « Accès à tout », « Bâtiment A, Salle 12 », « Aucun accès »… shown under each user. */
    fun resumeAcces(user: User): String = resumes[user.id].orEmpty()

    private fun resumer(user: User): String = runCatching {
        when {
            user.type == UserType.ADMIN -> "Accès à tout (administrateur)"
            !Services.acces.isRestricted(user) -> "Accès à tout"
            else -> Services.acces.getGrants(user).map { it.name.orEmpty() }.sorted().let { noms ->
                when {
                    noms.isEmpty() -> "Aucun accès"
                    noms.size <= 3 -> "Accès : " + noms.joinToString()
                    else -> "Accès : " + noms.take(3).joinToString() + " et ${noms.size - 3} autre(s)"
                }
            }
        }
    }.getOrDefault("")

    fun libelleType(salle: Room): String = Libelles.kind(salle.kind)

    fun ouvrirAcces(user: User) {
        if (!login.require(login.isAdmin)) return
        val u = Services.users.getById(user.id!!) ?: return
        accesUser = u
        accesRestreint = Services.acces.isRestricted(u)
        val accordes = Services.acces.getGrants(u).mapNotNull { it.id }.toSet()
        val racine = DefaultTreeNode<Room>()
        Services.hierarchie.getRoots().filter { it !is Device }.forEach { ajouterAcces(it, racine, accordes, herite = false) }
        accesArbre = racine
        accesSelection = null
    }

    // A ticked element ticks what it contains (it gives access to it).
    private fun ajouterAcces(salle: Room, parent: TreeNode<Room>, accordes: Set<Long>, herite: Boolean) {
        val noeud = DefaultTreeNode(salle, parent)
        val coche = herite || salle.id in accordes
        noeud.isSelected = coche
        noeud.isExpanded = salle.kind != RoomKind.ROOM
        Services.hierarchie.getChildren(salle).filter { it !is Device }.forEach { ajouterAcces(it, noeud, accordes, coche) }
    }

    /**
     * Records the ticked elements. An element counts with everything it contains (and what will
     * be added to it later) only if it is ticked with all its content; otherwise only its ticked
     * content counts: unticking one salle of an allowed étage limits the access to the others.
     */
    fun enregistrerAcces() {
        if (!login.require(login.isAdmin)) return
        val user = accesUser ?: return
        val coches = accesSelection.orEmpty().mapNotNull { it.data?.id }.toSet()
        val elements = if (accesRestreint) retenir(accesArbre, coches) else emptyList()
        try {
            Services.acces.define(user, accesRestreint, elements)
            Faces.info("Accès de « ${user.username} » enregistrés",
                if (accesRestreint) "${elements.size} élément(s) et leur contenu" else "Accès à tout")
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            load()
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
    }

    private fun retenir(noeud: TreeNode<Room>, coches: Set<Long>): List<Room> =
        noeud.children.flatMap { child -> if (complet(child, coches)) listOf(child.data) else retenir(child, coches) }

    private fun complet(noeud: TreeNode<Room>, coches: Set<Long>): Boolean =
        noeud.data?.id in coches && noeud.children.all { complet(it, coches) }

    fun isSelf(user: User): Boolean = login.isOwner(user)

    fun create() {
        selected = User()
        newPassword = ""
    }

    fun edit(user: User) {
        selected = Services.users.getById(user.id!!)
        newPassword = ""
    }

    fun save() {
        if (!login.require(login.isAdmin)) return
        val user = selected ?: return
        val creating = isNew(user.id)
        if (usernameTaken(user)) {
            Faces.error("Ce nom d'utilisateur est déjà pris")
            return
        }

        if (newPassword.isNotBlank()) {
            user.password = newPassword
        } else if (creating) {
            Faces.error("Le mot de passe est obligatoire")
            return
        }

        try {
            if (creating) {
                Services.users.add(user)
                Faces.info("Utilisateur « ${user.username} » créé",
                    if (user.type == UserType.ADMIN) null else "Il n'a encore accès à aucune salle : donnez-lui des accès avec le bouton Accès.")
            } else {
                Services.users.update(user)
                Faces.info("Utilisateur « ${user.username} » modifié")
            }
            newPassword = ""
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
            load()
        } catch (e: AlreadyExistsException) {
            Faces.error("Ce nom d'utilisateur est déjà pris", e.message)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
    }

    fun delete(user: User) {
        if (!login.require(login.isAdmin)) return
        if (login.isOwner(user)) {
            Faces.error("Supprime ton propre compte depuis la page Profil")
            return
        }
        try {
            Services.users.getById(user.id!!)?.let { Services.users.remove(it) }
            Faces.info("Utilisateur « ${user.username} » supprimé")
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
        }
        load()
    }
}
