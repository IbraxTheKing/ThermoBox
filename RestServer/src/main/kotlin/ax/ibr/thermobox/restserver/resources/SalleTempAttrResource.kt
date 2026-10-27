package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.thermobox.restserver.security.perimetre
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.time.LocalDateTime

@Path("api/salletemps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SalleTempAttrResource {

    private val service = BusinessFactory.getSalleTempAttrService()
    private val salleService = BusinessFactory.getSalleService()

    private val requestBoxes: ProtocolDriver = BusinessFactory.getDriver()

    @Context
    private lateinit var security: SecurityContext

    // One resource instance per request: the user's accesses are worked out once per request.
    private val acces by lazy { security.perimetre }

    /** The salle [id] if it exists and the user may see it; otherwise null, as for an unknown id. */
    private fun salle(id: Long) = salleService.getById(id)?.takeIf { acces.peutVoir(it) }

    @GET
    fun getAll(): List<SalleTempAttr> {
        return service.getAll().filter { acces.peutVoir(it.salle) }
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): SalleTempAttr? {
        return service.getById(id)?.takeIf { acces.peutVoir(it.salle) }
    }

    @GET
    @Path("/room/{id}")
    fun getBySalleId(@PathParam("id") id: Long): List<SalleTempAttr>? {
        if (!acces.peutVoir(id)) return null
        return service.getBySalle(id)
    }

    @GET
    @Path("/temperature/{id}")
    fun getByTemperatureId(@PathParam("id") id: Long): SalleTempAttr? {
        return service.getByTemperature(id)?.takeIf { acces.peutVoir(it.salle) }
    }

    @GET
    @Path("/room/{id}/current")
    fun getCurrentTemperaturesFromSalle(@PathParam("id") id: Long): List<Temperature>? {
        val salle = salle(id) ?: return null
        return service.getCurrentTemperaturesFromSalle(salle)
    }

    @GET
    @Path("/room/{id}/consigne")
    fun getConsigneFromSalle(@PathParam("id") id: Long): Consigne? {
        val salle = salle(id) ?: return null
        return service.getConsigneFromSalle(salle)
    }

    @GET
    @Path("/room/{id}/temperatures")
    fun getTemperaturesFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Temperature>? {
        val salle = salle(id) ?: return null
        return service.getTemperaturesFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
    }

    @GET
    @Path("/room/{id}/consignes")
    fun getConsignesFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Consigne>? {
        val salle = salle(id) ?: return null
        return service.getConsignesFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
    }

    @GET
    @Path("/room/{id}/average")
    fun getAverageTemperatureFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Temperature>? {
        val salle = salle(id) ?: return null
        return service.getAverageTemperatureFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
    }

    @POST
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], allowOwner = false)
    fun add(salleTempAttr: SalleTempAttr): Response {
        if (!acces.peutVoir(salleTempAttr.salle)) return Response.status(Response.Status.NOT_FOUND).build()
        return try {
            service.add(salleTempAttr)
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
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], allowOwner = false)
    fun updateById(@PathParam("id") id: Long, salleTempAttr: SalleTempAttr): Response {
        // Both the current salle and the new one must be allowed.
        val actuel = service.getById(id)?.takeIf { acces.peutVoir(it.salle) }
        if (actuel == null || !acces.peutVoir(salleTempAttr.salle)) return Response.status(Response.Status.NOT_FOUND).build()
        salleTempAttr.id = id
        service.update(salleTempAttr)
        return Response.noContent().build()
    }

    @DELETE
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN"], allowOwner = false)
    fun removeById(@PathParam("id") id: Long) {
        val attr = service.getById(id)
        if (attr != null) {
            service.remove(attr)
        }
    }

    @PUT
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], allowOwner = false)
    @Path("/room/{id}/consigne")
    fun sendConsigne(
        @PathParam("id") id: Long,
        @QueryParam("value") value: Float
    ): Response {
        val salle = salle(id)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        val consigne = Consigne(value, java.time.LocalDateTime.now())

        return try {
            service.add(SalleTempAttr(salle, consigne))

            if (requestBoxes != null) {
                try {
                    requestBoxes.sendConsigne(salle, consigne)
                } catch (ex: Exception) {
                    println(ex)
                }
            }

            Response.ok().build()
        } catch (ex: Exception) {
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to ex.message))
                .build()
        }
    }

    @GET
    @Path("/room/{id}/measured/latest")
    fun getLatestMeasured(@PathParam("id") id: Long): Temperature? {
        val salle = salle(id) ?: return null
        return service.getCurrentTemperatureFromSalle(salle, Mesurer(0f, LocalDateTime.now()))
    }
}