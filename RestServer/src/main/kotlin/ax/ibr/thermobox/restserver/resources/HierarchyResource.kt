package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.TreeDto
import ax.ibr.thermobox.restserver.dto.ElementRequest
import ax.ibr.thermobox.restserver.dto.ParentRequest
import ax.ibr.thermobox.restserver.hierarchy.UserView
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.scope
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
 * The building → floor → room → device tree, with temperatures and setpoints, as the current
 * user may see it; and its structure (create, rename, move: ADMIN). Deleting an element stays
 * DELETE /api/v2/rooms/{id}.
 */
@Path("api/v2/hierarchy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class HierarchyResource {

    private val rooms = BusinessFactory.getRoomService()
    private val hierarchy = BusinessFactory.getHierarchyService()

    @Context
    private lateinit var security: SecurityContext

    // One resource instance per request: the user's accesses are worked out once per request.
    private val view by lazy { UserView(security.currentUser, security.scope) }

    /** Every element the user may see, with its parent, temperature, setpoint, photo and favorite. */
    @GET
    @Path("/tree")
    fun tree(): TreeDto = view.tree()

    /** An element and what its page shows: ancestors, children, devices, rooms, own setpoints below. */
    @GET
    @Path("/elements/{id}")
    fun detail(@PathParam("id") id: Long): Response {
        val s = view.element(id) ?: return notFound()
        return Response.ok(view.detail(s)).build()
    }

    /** {"kind": "ROOM", "name": "Room 12", "parentId": 4}: a new element, attached or not. */
    @POST
    @Path("/elements")
    @RequiresRole("ADMIN")
    fun create(request: ElementRequest?): Response {
        if (request == null) return error("Missing body")
        val kind = request.kind?.let { k -> RoomKind.entries.firstOrNull { it.name.equals(k.trim(), ignoreCase = true) } }
            ?: return error("kind must be one of ${RoomKind.entries.joinToString { it.name }}")
        val name = request.name?.trim().orEmpty()
        if (name.isEmpty()) return error("name is required")
        val parent = request.parentId?.let { pid -> rooms.getById(pid) ?: return error("Unknown parent $pid") }
        if (parent != null && parent.kind !in kind.parentKinds) return error(refusal(kind, parent))

        val room = kind.create(name)
        rooms.add(room)
        return try {
            hierarchy.setParent(room, parent)
            Response.status(Response.Status.CREATED).entity(view.node(room)).build()
        } catch (e: IllegalArgumentException) {
            // Not kept half made: an element that can't go where it was asked is not created.
            rooms.remove(room)
            error(e.message ?: refusal(kind, parent))
        }
    }

    /** {"name": "…", "parentId": 4 or null}: renames and moves (null detaches). The kind never changes. */
    @PUT
    @Path("/elements/{id}")
    @RequiresRole("ADMIN")
    fun update(@PathParam("id") id: Long, request: ElementRequest?): Response {
        val room = rooms.getById(id) ?: return notFound()
        if (request == null) return error("Missing body")
        val name = request.name?.trim().orEmpty()
        if (name.isEmpty()) return error("name is required")
        val parent = request.parentId?.let { pid -> rooms.getById(pid) ?: return error("Unknown parent $pid") }
        if (parent != null && parent.kind !in room.kind.parentKinds) return error(refusal(room.kind, parent))
        return try {
            hierarchy.setParent(room, parent)
            if (room.name != name) {
                room.name = name
                rooms.update(room)
            }
            Response.ok(view.node(room)).build()
        } catch (e: IllegalArgumentException) {
            error(e.message ?: refusal(room.kind, parent))
        }
    }

    /** {"parentId": 4}: moves the element into another one; {"parentId": null} detaches it. */
    @PUT
    @Path("/elements/{id}/parent")
    @RequiresRole("ADMIN")
    fun move(@PathParam("id") id: Long, request: ParentRequest?): Response {
        val room = rooms.getById(id) ?: return notFound()
        val parent = request?.parentId?.let { pid -> rooms.getById(pid) ?: return error("Unknown parent $pid") }
        return try {
            hierarchy.setParent(room, parent)
            Response.ok(view.node(room)).build()
        } catch (e: IllegalArgumentException) {
            error(e.message ?: refusal(room.kind, parent))
        }
    }

    private fun refusal(kind: RoomKind, parent: Room?): String =
        "${kind.withArticle.replaceFirstChar { it.uppercase() }} cannot go into ${parent?.kind?.withArticle ?: "nothing"}"

    private fun notFound(): Response = Response.status(Response.Status.NOT_FOUND).build()

    private fun error(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}
