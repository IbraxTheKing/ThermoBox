package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.scope
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

/** Rooms the current user pinned on their dashboard. Only rooms go on the dashboard. */
@Path("api/v2/favorites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FavoriteResource {

    private val favorites = BusinessFactory.getFavoriteService()
    private val rooms = BusinessFactory.getRoomService()

    @Context
    private lateinit var security: SecurityContext

    /** Ids of the rooms pinned (and still allowed). */
    @GET
    fun list(): List<Long> {
        val access = security.scope
        return favorites.getByUser(security.currentUser).map { it.room }.filter { access.canSee(it) }.mapNotNull { it.id }
    }

    /** Pins room [id]; already pinned changes nothing. */
    @PUT
    @Path("/{id}")
    fun add(@PathParam("id") id: Long): Response {
        val room = rooms.getById(id)?.takeIf { security.scope.canSee(it) }
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (room.kind != RoomKind.ROOM) {
            return Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to "Only a room can be a favorite")).build()
        }
        val user = security.currentUser
        if (favorites.getByUser(user).none { it.room.id == id }) favorites.add(Favorite(user, room))
        return Response.noContent().build()
    }

    /** Unpins room [id]; not pinned changes nothing. */
    @DELETE
    @Path("/{id}")
    fun remove(@PathParam("id") id: Long): Response {
        favorites.getByUser(security.currentUser).filter { it.room.id == id }.forEach { favorites.remove(it) }
        return Response.noContent().build()
    }
}
