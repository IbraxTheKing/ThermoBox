package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDateTime

@Path("/temperatures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TemperatureResource {

    private val service = BusinessFactory().getTemperatureService()

    @GET
    fun getAll(): List<Temperature> {
        return service.getAll()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): Temperature? {
        return service.getById(id)
    }

    // Le paramètre "type" sert de discriminant Consigne/Mesurer :
    // on instancie un objet vide du type demandé côté client via une valeur simple (ex: "consigne"/"mesurer"),
    // à adapter selon comment tu veux exposer TYPE(...) publiquement.
    @GET
    @Path("/type/{type}")
    fun getByType(@PathParam("type") type: String): List<Temperature>? {
        val sample = resolveSample(type) ?: return null
        return service.getByType(sample)
    }

    @GET
    @Path("/average/{type}")
    fun getAverage(@PathParam("type") type: String): Temperature? {
        val sample = resolveSample(type) ?: return null
        return service.getAverage(sample)
    }

    @POST
    @RequiresRole("ADMIN")
    fun add(temperature: Temperature): Response {
        return try {
            service.add(temperature)
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
    fun updateById(@PathParam("id") id: Long, temperature: Temperature) {
        temperature.id = id
        service.update(temperature)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun removeById(@PathParam("id") id: Long) {
        val temperature = service.getById(id)
        if (temperature != null) {
            service.remove(temperature)
        }
    }

    private fun resolveSample(type: String): Temperature? {
        return when (type.lowercase()) {
            "consigne" -> ax.ibr.thermobox.common.entities.Consigne(0f, LocalDateTime.now())
            "mesurer" -> ax.ibr.thermobox.common.entities.Mesurer(0f, LocalDateTime.now())
            else -> null
        }
    }
}