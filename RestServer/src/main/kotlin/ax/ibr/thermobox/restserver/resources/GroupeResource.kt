package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.GroupeDetail
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/** Groups of users and the bâtiments / étages / salles they give access to (ADMIN). */
@Path("api/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class GroupeResource {
    private val groupes = BusinessFactory.getGroupeService()
    private val users = BusinessFactory.getUserService()
    private val salles = BusinessFactory.getSalleService()

    @GET
    @RequiresRole("ADMIN")
    fun lister(): List<GroupeDto> = groupes.getAll().map { GroupeDto.of(groupes.detail(it)) }

    @GET
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun lire(@PathParam("id") id: Long): Response {
        val g = groupes.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        return Response.ok(GroupeDto.of(groupes.detail(g))).build()
    }

    /** {"name": "...", "description": "...", "members": [user ids], "elements": [bâtiment / étage / salle ids]} */
    @POST
    @RequiresRole("ADMIN")
    fun creer(demande: GroupeRequest?): Response = enregistrer(null, demande, Response.Status.CREATED)

    @PUT
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun modifier(@PathParam("id") id: Long, demande: GroupeRequest?): Response {
        val g = groupes.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        return enregistrer(g.id, demande, Response.Status.OK)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun supprimer(@PathParam("id") id: Long): Response {
        val g = groupes.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        groupes.supprimer(g)
        return Response.noContent().build()
    }

    private fun enregistrer(id: Long?, d: GroupeRequest?, statut: Response.Status): Response {
        if (d == null) return erreur("Missing body")
        val membres = d.members.map { users.getById(it) ?: return erreur("Unknown user $it") }
        val elements = d.elements.map { salles.getById(it) ?: return erreur("Unknown element $it") }
        return try {
            val g = groupes.enregistrer(id?.let { groupes.getById(it) }, d.name, d.description, membres, elements)
            Response.status(statut).entity(GroupeDto.of(groupes.detail(g))).build()
        } catch (e: IllegalArgumentException) {
            erreur(e.message ?: "Invalid group")
        }
    }

    private fun erreur(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}

data class GroupeMembreDto(val id: Long?, val username: String?)
data class GroupeElementDto(val id: Long?, val name: String?, val kind: String)

data class GroupeDto(val id: Long?, val name: String, val description: String?, val members: List<GroupeMembreDto>, val elements: List<GroupeElementDto>) {
    companion object {
        fun of(d: GroupeDetail) = GroupeDto(
            d.groupe.id, d.groupe.nom, d.groupe.description,
            d.membres.map { GroupeMembreDto(it.id, it.username) },
            d.elements.map { GroupeElementDto(it.id, it.name, it.kind.name) },
        )
    }
}

class GroupeRequest {
    var name: String = ""
    var description: String? = null
    var members: List<Long> = emptyList()
    var elements: List<Long> = emptyList()
}
