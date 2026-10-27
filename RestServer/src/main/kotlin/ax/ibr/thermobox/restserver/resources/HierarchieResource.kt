package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.ArbreDto
import ax.ibr.thermobox.restserver.dto.ElementRequest
import ax.ibr.thermobox.restserver.dto.ParentRequest
import ax.ibr.thermobox.restserver.hierarchie.VueUtilisateur
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.perimetre
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

/**
 * The bâtiment → étage → salle → boîtier tree, with temperatures and consignes, as the current
 * user may see it; and its structure (create, rename, move: ADMIN). Deleting an element stays
 * DELETE /api/salles/{id}.
 */
@Path("api/hierarchie")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class HierarchieResource {

    private val salles = BusinessFactory.getSalleService()
    private val hierarchie = BusinessFactory.getHierarchieService()

    @Context
    private lateinit var security: SecurityContext

    // One resource instance per request: the user's accesses are worked out once per request.
    private val vue by lazy { VueUtilisateur(security.currentUser, security.perimetre) }

    /** Every element the user may see, with its parent, temperature, consigne, photo and favourite. */
    @GET
    @Path("/arbre")
    fun arbre(): ArbreDto = vue.arbre()

    /** An element and what its page shows: ancestors, children, boxes, salles, own consignes below. */
    @GET
    @Path("/elements/{id}")
    fun detail(@PathParam("id") id: Long): Response {
        val s = vue.element(id) ?: return introuvable()
        return Response.ok(vue.detail(s)).build()
    }

    /** {"kind": "SALLE", "name": "Salle 12", "parentId": 4}: a new element, attached or not. */
    @POST
    @Path("/elements")
    @RequiresRole("ADMIN")
    fun creer(demande: ElementRequest?): Response {
        if (demande == null) return erreur("Missing body")
        val kind = demande.kind?.let { k -> SalleKind.entries.firstOrNull { it.name.equals(k.trim(), ignoreCase = true) } }
            ?: return erreur("kind must be one of ${SalleKind.entries.joinToString { it.name }}")
        val nom = demande.name?.trim().orEmpty()
        if (nom.isEmpty()) return erreur("name is required")
        val parent = demande.parentId?.let { pid -> salles.getById(pid) ?: return erreur("Unknown parent $pid") }
        if (parent != null && parent.kind !in kind.parentKinds) return erreur(refus(kind, parent))

        val salle = kind.create(nom)
        salles.add(salle)
        return try {
            hierarchie.setParent(salle, parent)
            Response.status(Response.Status.CREATED).entity(vue.noeud(salle)).build()
        } catch (e: IllegalArgumentException) {
            // Not kept half made: an element that can't go where it was asked is not created.
            salles.remove(salle)
            erreur(e.message ?: refus(kind, parent))
        }
    }

    /** {"name": "…", "parentId": 4 or null}: renames and moves (null detaches). The kind never changes. */
    @PUT
    @Path("/elements/{id}")
    @RequiresRole("ADMIN")
    fun modifier(@PathParam("id") id: Long, demande: ElementRequest?): Response {
        val salle = salles.getById(id) ?: return introuvable()
        if (demande == null) return erreur("Missing body")
        val nom = demande.name?.trim().orEmpty()
        if (nom.isEmpty()) return erreur("name is required")
        val parent = demande.parentId?.let { pid -> salles.getById(pid) ?: return erreur("Unknown parent $pid") }
        if (parent != null && parent.kind !in salle.kind.parentKinds) return erreur(refus(salle.kind, parent))
        return try {
            hierarchie.setParent(salle, parent)
            if (salle.name != nom) {
                salle.name = nom
                salles.update(salle)
            }
            Response.ok(vue.noeud(salle)).build()
        } catch (e: IllegalArgumentException) {
            erreur(e.message ?: refus(salle.kind, parent))
        }
    }

    /** {"parentId": 4}: moves the element into another one; {"parentId": null} detaches it. */
    @PUT
    @Path("/elements/{id}/parent")
    @RequiresRole("ADMIN")
    fun deplacer(@PathParam("id") id: Long, demande: ParentRequest?): Response {
        val salle = salles.getById(id) ?: return introuvable()
        val parent = demande?.parentId?.let { pid -> salles.getById(pid) ?: return erreur("Unknown parent $pid") }
        return try {
            hierarchie.setParent(salle, parent)
            Response.ok(vue.noeud(salle)).build()
        } catch (e: IllegalArgumentException) {
            erreur(e.message ?: refus(salle.kind, parent))
        }
    }

    private fun refus(kind: SalleKind, parent: Salle?): String =
        "${kind.withArticle.replaceFirstChar { it.uppercase() }} ne peut pas aller dans ${parent?.kind?.withArticle ?: "rien"}"

    private fun introuvable(): Response = Response.status(Response.Status.NOT_FOUND).build()

    private fun erreur(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}
