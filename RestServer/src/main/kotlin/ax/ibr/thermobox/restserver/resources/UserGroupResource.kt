package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.UserGroupDetail
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

/** Groups of users and the buildings / floors / rooms they give access to (ADMIN). */
@Path("api/v2/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserGroupResource {
    private val groups = BusinessFactory.getUserGroupService()
    private val users = BusinessFactory.getUserService()
    private val rooms = BusinessFactory.getRoomService()

    @GET
    @RequiresRole("ADMIN")
    fun list(): List<UserGroupDto> = groups.getAll().map { UserGroupDto.of(groups.detail(it)) }

    @GET
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun read(@PathParam("id") id: Long): Response {
        val g = groups.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        return Response.ok(UserGroupDto.of(groups.detail(g))).build()
    }

    /** {"name": "...", "description": "...", "members": [user ids], "elements": [building / floor / room ids]} */
    @POST
    @RequiresRole("ADMIN")
    fun create(request: UserGroupRequest?): Response = save(null, request, Response.Status.CREATED)

    @PUT
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun update(@PathParam("id") id: Long, request: UserGroupRequest?): Response {
        val g = groups.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        return save(g.id, request, Response.Status.OK)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun delete(@PathParam("id") id: Long): Response {
        val g = groups.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        groups.deleteGroup(g)
        return Response.noContent().build()
    }

    private fun save(id: Long?, d: UserGroupRequest?, status: Response.Status): Response {
        if (d == null) return error("Missing body")
        val members = d.members.map { users.getById(it) ?: return error("Unknown user $it") }
        val elements = d.elements.map { rooms.getById(it) ?: return error("Unknown element $it") }
        return try {
            val g = groups.save(id?.let { groups.getById(it) }, d.name, d.description, members, elements)
            Response.status(status).entity(UserGroupDto.of(groups.detail(g))).build()
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "Invalid group")
        }
    }

    private fun error(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}

data class UserGroupMemberDto(val id: Long?, val username: String?)
data class UserGroupElementDto(val id: Long?, val name: String?, val kind: String)

data class UserGroupDto(val id: Long?, val name: String, val description: String?, val members: List<UserGroupMemberDto>, val elements: List<UserGroupElementDto>) {
    companion object {
        fun of(d: UserGroupDetail) = UserGroupDto(
            d.group.id, d.group.name, d.group.description,
            d.members.map { UserGroupMemberDto(it.id, it.username) },
            d.elements.map { UserGroupElementDto(it.id, it.name, it.kind.name) },
        )
    }
}

class UserGroupRequest {
    var name: String = ""
    var description: String? = null
    var members: List<Long> = emptyList()
    var elements: List<Long> = emptyList()
}
