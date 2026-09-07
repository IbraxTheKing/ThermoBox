package ax.ibr.thermobox.restserver.resources

import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserResource {
    private val service = BusinessFactory().getUserService()

    @GET
    @Path("/{id}")
    fun getById(
        @PathParam("id") id: Long
    ): User? {
        return service.getById(id)
    }

    @GET
    @RequiresRole("ADMIN")
    fun getAll(): List<User> {
        return service.getAll()
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun add(user: User): Response {
        return try {
            service.add(user)

            Response
                .status(Response.Status.CREATED)
                .build()

        } catch (e: AlreadyExistsException) {

            Response
                .status(Response.Status.CONFLICT) // 409
                .entity(
                    mapOf(
                        "error" to e.message
                    )
                )
                .build()
        }
    }

    @PUT
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun updateById(
        @PathParam("id") id: Long,
        user: User
    ) {
        user.id = id
        service.update(user)
    }

    @DELETE
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = true)
    fun removeById(
        @PathParam("id") id: Long
    ) {
        val user = service.getById(id)

        if (user != null) {
            service.remove(user)

        }
    }

    @GET
    @Path("/username/{username}")
    fun getByUsername(
        @PathParam("username") username: String
    ): List<User> {
        return service.getByUsername(username)
    }
}