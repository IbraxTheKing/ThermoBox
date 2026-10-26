package ax.ibr.thermobox.restserver.resources


import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.restserver.dto.AccessDto
import ax.ibr.thermobox.restserver.dto.AccessRequest
import ax.ibr.thermobox.restserver.dto.UserDto
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.PublicEndpoint
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
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
import javax.naming.InvalidNameException


@Path("api/v2/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserResource {
    private val service = BusinessFactory.getUserService()
    private val roomService = BusinessFactory.getRoomService()
    private val access = BusinessFactory.getAccessService()

    @GET
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun getById(
        @PathParam("id") id: Long
    ): Response {
        val user = service.getById(id) ?: return notFound()
        return Response.ok(UserDto.from(user)).build()
    }

    @GET
    @RequiresRole("ADMIN")
    fun getAll(): List<UserDto> {
        return service.getAll().map(UserDto::from)
    }

    /** Public registration: the account is always created as VIEWER, whatever the body says. */
    @POST
    @PublicEndpoint
    fun add(user: User?): Response {
        val username = user?.username?.trim().orEmpty()
        val password = user?.password.orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "username and password are required")
        }
        if (PasswordHasher.isTooLong(password)) return passwordTooLong()
        if (service.getByUsername(username) != null) {
            return error(Response.Status.CONFLICT, "Username already taken")
        }

        return try {
            service.add(User(username, password))
            Response
                .status(Response.Status.CREATED)
                .build()
        } catch (e: AlreadyExistsException) {
            error(Response.Status.CONFLICT, e.message ?: "Username already taken")
        } catch (e: InvalidNameException) {
            error(Response.Status.CONFLICT, e.message ?: "Username is not a valid username")
        } catch (e: Exception) {
            error(Response.Status.BAD_REQUEST, e.message ?: "Username is not a valid username: ${e.message}")
        }
    }

    /**
     * Partial update: only non-empty fields are applied.
     * Changing the role is reserved to admins, so an owner can't promote themselves.
     */
    @PUT
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun updateById(
        @PathParam("id") id: Long,
        changes: User?,
        @Context securityContext: SecurityContext
    ): Response {
        val existing = service.getById(id) ?: return notFound()
        if (changes == null) return error(Response.Status.BAD_REQUEST, "Missing body")

        if (changes.type != null && changes.type != existing.type && !securityContext.isUserInRole("ADMIN")) {
            return error(Response.Status.FORBIDDEN, "Only an admin can change a role")
        }

        val newUsername = changes.username?.trim()
        if (!newUsername.isNullOrEmpty() && newUsername != existing.username) {
            if (service.getByUsername(newUsername) != null) {
                return error(Response.Status.CONFLICT, "Username already taken")
            }
            existing.username = newUsername
        }
        if (!changes.password.isNullOrEmpty()) {
            if (PasswordHasher.isTooLong(changes.password!!)) return passwordTooLong()
            existing.password = changes.password // hashed by the UserService
        }
        if (changes.type != null) existing.type = changes.type

        service.update(existing)
        return Response.ok(UserDto.from(existing)).build()
    }

    @DELETE
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun removeById(
        @PathParam("id") id: Long
    ): Response {
        val user = service.getById(id) ?: return notFound()
        service.remove(user)
        return Response.noContent().build()
    }

    @GET
    @Path("/username/{username}")
    @RequiresRole("ADMIN")
    fun getByUsername(
        @PathParam("username") username: String
    ): Response {
        val user = service.getByUsername(username) ?: return notFound()
        return Response.ok(UserDto.from(user)).build()
    }

    /**
     * Accesses of a user: unlimited, or limited to some buildings / floors / rooms and what
     * they contain. An ADMIN reads anyone's, a user their own.
     */
    @GET
    @Path("/{id}/access")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun getAccess(@PathParam("id") id: Long): Response {
        val user = service.getById(id) ?: return notFound()
        return Response.ok(AccessDto.of(user)).build()
    }

    /** Replaces the accesses of a user (ADMIN). An ADMIN account can't be limited. */
    @PUT
    @Path("/{id}/access")
    @RequiresRole("ADMIN")
    fun setAccess(@PathParam("id") id: Long, request: AccessRequest?): Response {
        val user = service.getById(id) ?: return notFound()
        if (request == null) return error(Response.Status.BAD_REQUEST, "Missing body")
        val rooms = request.elements.map { sid ->
            roomService.getById(sid) ?: return error(Response.Status.BAD_REQUEST, "Unknown element $sid")
        }
        return try {
            access.define(user, request.restricted, rooms)
            Response.ok(AccessDto.of(user)).build()
        } catch (e: IllegalArgumentException) {
            error(Response.Status.BAD_REQUEST, e.message ?: "Invalid accesses")
        }
    }

    /** BCrypt only uses the first 72 bytes: refuse rather than silently ignore the rest. */
    private fun passwordTooLong(): Response =
        error(Response.Status.BAD_REQUEST, "password must be at most ${PasswordHasher.MAX_BYTES} bytes")

    private fun notFound(): Response = Response.status(Response.Status.NOT_FOUND).build()

    private fun error(status: Response.Status, message: String): Response =
        Response.status(status).entity(mapOf("error" to message)).build()
}
