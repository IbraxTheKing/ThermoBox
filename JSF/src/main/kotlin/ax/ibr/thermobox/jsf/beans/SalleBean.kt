package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import jakarta.annotation.PostConstruct
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import org.primefaces.model.DefaultTreeNode
import org.primefaces.model.TreeNode
import java.io.Serializable

/** One row of the structure tree. [temperature] is the box's last measure, or the average of the boxes below. */
data class Noeud(val salle: Salle, val temperature: Float?, val boitiers: Int, val rattache: Boolean) : Serializable {
    val kind: SalleKind get() = salle.kind

    val icon: String
        get() = when (kind) {
            SalleKind.BATIMENT -> "pi-building"
            SalleKind.ETAGE -> "pi-server"
            SalleKind.SALLE -> "pi-home"
            SalleKind.BOITIER -> "pi-wifi"
        }
}

/** Bâtiments → étages → salles → boîtiers : arbre + CRUD (ADMIN). Les liens passent par SalleSalleAttr. */
@Named
@ViewScoped
class SalleBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    var arbre: TreeNode<Noeud> = DefaultTreeNode()

    // Formulaire du dialogue (création ou modification)
    var editingId: Long? = null
    var formKind: SalleKind = SalleKind.BATIMENT
    var formName: String = ""
    var formParentId: Long? = null
    var parentsPossibles: List<Salle> = emptyList()

    val kinds: List<SalleKind> = SalleKind.entries.toList()
    val isEditMode: Boolean get() = editingId != null

    @PostConstruct
    fun load() {
        val racine = DefaultTreeNode<Noeud>()
        Services.hierarchie.getRacines().forEach { ajouter(it, racine, rattache = it.kind == SalleKind.BATIMENT) }
        arbre = racine
    }

    private fun ajouter(salle: Salle, parent: TreeNode<Noeud>, rattache: Boolean) {
        val noeud = DefaultTreeNode(noeud(salle, rattache), parent)
        noeud.isExpanded = salle.kind != SalleKind.SALLE
        Services.hierarchie.getChildren(salle).forEach { ajouter(it, noeud, rattache = true) }
    }

    private fun noeud(salle: Salle, rattache: Boolean): Noeud {
        val mesures = Services.climat.getMesures(salle)
        val temperature = if (salle is Boitier) mesures.firstOrNull()?.mesure?.value else Services.climat.getMoyenne(salle)
        return Noeud(salle, temperature, mesures.size, rattache)
    }

    /** Nouvel élément, éventuellement déjà rattaché à [parent]. */
    fun create(kind: SalleKind, parent: Salle?) {
        editingId = null
        formKind = kind
        formName = ""
        formParentId = parent?.id
        refreshParents()
    }

    fun edit(salle: Salle) {
        editingId = salle.id
        formKind = salle.kind
        formName = salle.name.orEmpty()
        formParentId = Services.hierarchie.getParent(salle)?.id
        refreshParents()
    }

    /** Changement de type dans le dialogue : les parents possibles changent aussi. */
    fun refreshParents() {
        parentsPossibles = formKind.parentKind
            ?.let { Services.salles.getAllByType(it.create(null)) }
            ?.sortedBy { it.name?.lowercase() }
            .orEmpty()
        if (parentsPossibles.none { it.id == formParentId }) formParentId = null
    }

    fun save() {
        if (!login.require(login.isAdmin)) return
        val name = formName.trim()
        if (name.isEmpty()) {
            Faces.error("Le nom est obligatoire")
            return
        }
        try {
            // Fresh copy from the database: the type of an existing element never changes.
            val salle = editingId?.let { Services.salles.getById(it) } ?: formKind.create(name)
            salle.name = name
            if (salle.id == null) Services.salles.add(salle) else Services.salles.update(salle)

            val parent = formParentId?.let { Services.salles.getById(it) }
            Services.hierarchie.setParent(salle, parent)

            Faces.info("${salle.kind.label} « $name » enregistré(e)")
            PrimeFaces.current().ajax().addCallbackParam("saved", true)
        } catch (e: IllegalArgumentException) {
            Faces.error("Rattachement impossible", e.message)
        } catch (e: Exception) {
            Faces.error("Enregistrement impossible", e.message)
        }
        load()
    }

    fun detacher(salle: Salle) {
        if (!login.require(login.isAdmin)) return
        try {
            Services.hierarchie.setParent(salle, null)
            Faces.info("« ${salle.name} » n'est plus rattaché(e)")
        } catch (e: Exception) {
            Faces.error("Détachement impossible", e.message)
        }
        load()
    }

    fun delete(salle: Salle) {
        if (!login.require(login.isAdmin)) return
        try {
            Services.salles.getById(salle.id!!)?.let { Services.salles.remove(it) }
            Faces.info("« ${salle.name} » supprimé(e)")
        } catch (e: Exception) {
            Faces.error("Suppression impossible", e.message)
        }
        load()
    }
}
