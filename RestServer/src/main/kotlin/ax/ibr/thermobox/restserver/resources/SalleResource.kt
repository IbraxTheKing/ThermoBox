package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("api/salles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SalleResource {

    private val service = BusinessFactory().getSalleService()

    @GET
    fun getAll(): List<Salle> {
        return service.getAll()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): Salle? {
        return service.getById(id)
    }

    @GET
    @Path("/name/{name}")
    fun getByName(@PathParam("name") name: String): Salle? {
        return service.getByName(name)
    }

    @POST
    @RequiresRole("ADMIN")
    fun add(salle: Salle): Response {
        return try {
            service.add(salle)
            Response
                .status(Response.Status.CREATED)
                .build()
        } catch (e: AlreadyExistsException) {
            Response
                .status(Response.Status.CONFLICT)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }

    @PUT
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun updateById(@PathParam("id") id: Long, salle: Salle) {
        salle.id = id
        service.update(salle)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun removeById(@PathParam("id") id: Long) {
        val salle = service.getById(id)
        if (salle != null) {
            service.remove(salle)
        }
    }
}