package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.perimetre
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

/** Salles the current user pinned on their dashboard. Only salles go on the dashboard. */
@Path("api/favoris")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FavoriResource {

    private val favoris = BusinessFactory.getFavoriService()
    private val salles = BusinessFactory.getSalleService()

    @Context
    private lateinit var security: SecurityContext

    /** Ids of the salles pinned (and still allowed). */
    @GET
    fun lister(): List<Long> {
        val acces = security.perimetre
        return favoris.getByUser(security.currentUser).map { it.salle }.filter { acces.peutVoir(it) }.mapNotNull { it.id }
    }

    /** Pins salle [id]; already pinned changes nothing. */
    @PUT
    @Path("/{id}")
    fun ajouter(@PathParam("id") id: Long): Response {
        val salle = salles.getById(id)?.takeIf { security.perimetre.peutVoir(it) }
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (salle.kind != SalleKind.SALLE) {
            return Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to "Only a salle can be a favourite")).build()
        }
        val user = security.currentUser
        if (favoris.getByUser(user).none { it.salle.id == id }) favoris.add(Favori(user, salle))
        return Response.noContent().build()
    }

    /** Unpins salle [id]; not pinned changes nothing. */
    @DELETE
    @Path("/{id}")
    fun retirer(@PathParam("id") id: Long): Response {
        favoris.getByUser(security.currentUser).filter { it.salle.id == id }.forEach { favoris.remove(it) }
        return Response.noContent().build()
    }
}
