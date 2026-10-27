package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.thermobox.restserver.security.perimetre
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.Response
import java.time.LocalDateTime

@Path("api/temperatures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TemperatureResource {

    private val service = BusinessFactory.getTemperatureService()
    private val accesService = BusinessFactory.getAccesService()

    @Context
    private lateinit var security: SecurityContext

    // Temperatures of the salles / boxes the user may see only (all of them for unlimited users).
    private val acces by lazy { security.perimetre }

    private fun autorisees(liste: List<Temperature>): List<Temperature> = accesService.filtrerTemperatures(acces, liste)

    @GET
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE", "VIEWER"])
    fun getAll(): List<Temperature> {
        return autorisees(service.getAll())
    }

    @GET
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE", "VIEWER"])
    fun getById(@PathParam("id") id: Long): Temperature? {
        return service.getById(id)?.let { autorisees(listOf(it)).firstOrNull() }
    }

    @GET
    @Path("/type/{type}")
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE", "VIEWER"])
    fun getByType(@PathParam("type") type: String): List<Temperature>? {
        val sample = resolveSample(type) ?: return null
        return service.getByType(sample)?.let { autorisees(it) }
    }

    @GET
    @Path("/average/{type}")
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE", "VIEWER"])
    fun getAverage(@PathParam("type") type: String): Temperature? {
        val sample = resolveSample(type) ?: return null
        if (acces.tout) return service.getAverage(sample)
        // Limited user: the average over their temperatures only.
        val valeurs = autorisees(service.getByType(sample).orEmpty()).mapNotNull { it.value }
        if (valeurs.isEmpty()) return null
        return sample.apply { value = valeurs.average().toFloat() }
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

    /**
     * Adds a measure ("mesurer") or a consigne ("consigne"): {"value": 21.5, "date": …}. The plain
     * POST above can't say which one it is.
     */
    @POST
    @Path("/type/{type}")
    @RequiresRole("ADMIN")
    fun addTyped(@PathParam("type") type: String, temperature: Temperature?): Response {
        val sample = resolveSample(type) ?: return Response.status(Response.Status.NOT_FOUND).build()
        val value = temperature?.value
        if (temperature == null || value == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to "value is required")).build()
        }
        sample.value = value
        sample.date = temperature.date ?: LocalDateTime.now()
        return add(sample)
    }

    @PUT
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun updateById(@PathParam("id") id: Long, temperature: Temperature) {
        // Changes the stored row, so a consigne stays a consigne and a measure a measure.
        val existante = service.getById(id)
        if (existante == null) {
            temperature.id = id
            service.update(temperature)
            return
        }
        temperature.value?.let { existante.value = it }
        temperature.date?.let { existante.date = it }
        service.update(existante)
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