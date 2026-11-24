package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.rules.ConsigneRules
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
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

    @GET
    fun getAll(): List<SalleTempAttr> {
        return service.getAll()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): SalleTempAttr? {
        return service.getById(id)
    }

    @GET
    @Path("/room/{id}")
    fun getBySalleId(@PathParam("id") id: Long): List<SalleTempAttr>? {
        return service.getBySalle(id)
    }

    @GET
    @Path("/temperature/{id}")
    fun getByTemperatureId(@PathParam("id") id: Long): SalleTempAttr? {
        return service.getByTemperature(id)
    }

    @GET
    @Path("/room/{id}/current")
    fun getCurrentTemperaturesFromSalle(@PathParam("id") id: Long): List<Temperature>? {
        val salle = salleService.getById(id) ?: return null
        return service.getCurrentTemperaturesFromSalle(salle)
    }

    @GET
    @Path("/room/{id}/consigne")
    fun getConsigneFromSalle(@PathParam("id") id: Long): Consigne? {
        val salle = salleService.getById(id) ?: return null
        return service.getConsigneFromSalle(salle)
    }

    @GET
    @Path("/room/{id}/temperatures")
    fun getTemperaturesFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Temperature>? {
        val salle = salleService.getById(id) ?: return null
        return service.getTemperaturesFromSalleTimed(salle, instant(start, "start"), instant(end, "end"))
    }

    @GET
    @Path("/room/{id}/consignes")
    fun getConsignesFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Consigne>? {
        val salle = salleService.getById(id) ?: return null
        return service.getConsignesFromSalleTimed(salle, instant(start, "start"), instant(end, "end"))
    }

    @GET
    @Path("/room/{id}/average")
    fun getAverageTemperatureFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Temperature>? {
        val salle = salleService.getById(id) ?: return null
        return service.getAverageTemperatureFromSalleTimed(salle, instant(start, "start"), instant(end, "end"))
    }

    /** ?start= / ?end= required and readable: missing or malformed, it was a 500. */
    private fun instant(value: String?, name: String): Instant = try {
        Instant.parse(requireNotNull(value?.takeIf { it.isNotBlank() }) { "$name is required (ISO-8601 instant)" })
    } catch (e: java.time.format.DateTimeParseException) {
        throw BadRequestException("$name must be an ISO-8601 instant")
    } catch (e: IllegalArgumentException) {
        throw BadRequestException(e.message)
    }

    @POST
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], allowOwner = false)
    fun add(salleTempAttr: SalleTempAttr): Response {
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
    fun updateById(@PathParam("id") id: Long, salleTempAttr: SalleTempAttr) {
        salleTempAttr.id = id
        service.update(salleTempAttr)
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
        @QueryParam("value") value: Float?
    ): Response {
        val salle = salleService.getById(id)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        // Without ?value=, a Float parameter was 0: a consigne of 0 °C was saved
        if (value == null) return refusee("value is required")
        // Checked before saving: the driver checked after, and its refusal was only printed, so a
        // refused consigne (too hot, impossible, NaN) was kept and the answer was 200
        try {
            ConsigneRules.check(value)
        } catch (e: Exception) {
            return refusee(e.message ?: "Consigne refusée")
        }

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
            // The cause goes to the server's log, not to the caller: a database message holds the SQL
            System.err.println("[ThermoBox] consigne of salle $id not saved: ${ex.message}")
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Consigne not saved"))
                .build()
        }
    }

    private fun refusee(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

    @GET
    @Path("/room/{id}/measured/latest")
    fun getLatestMeasured(@PathParam("id") id: Long): Temperature? {
        val salle = salleService.getById(id) ?: return null
        return service.getCurrentTemperatureFromSalle(salle, Mesurer(0f, LocalDateTime.now()))
    }
}