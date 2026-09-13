package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.sql.Date
import java.time.Instant
import java.time.LocalDateTime

@Path("/salletemps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SalleTempAttrResource {

    private val service = BusinessFactory().getSalleTempAttrService()
    private val salleService = BusinessFactory().getSalleService()

    // À injecter/initialiser avec ton driver actif (MqttSendReceiver ou SimulatedProtocolDriver)
    // selon la configuration de ton serveur REST — non résolu ici faute de mécanisme de DI connu.
    private lateinit var requestBoxes: ProtocolDriver

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
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Temperature>? {
        val salle = salleService.getById(id) ?: return null
        return service.getTemperaturesFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
    }

    @GET
    @Path("/room/{id}/consignes")
    fun getConsignesFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Consigne>? {
        val salle = salleService.getById(id) ?: return null
        return service.getConsignesFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
    }

    @GET
    @Path("/room/{id}/average")
    fun getAverageTemperatureFromSalleTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String,
        @QueryParam("end") end: String
    ): List<Temperature>? {
        val salle = salleService.getById(id) ?: return null
        return service.getAverageTemperatureFromSalleTimed(salle, Instant.parse(start), Instant.parse(end))
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
        @QueryParam("value") value: Float
    ): Response {
        val salle = salleService.getById(id)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        val consigne = Consigne(value, LocalDateTime.now())
        return try {
            requestBoxes.sendConsigne(salle, consigne)
            Response.ok().build()
        } catch (ex: Exception) {
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to ex.message))
                .build()
        }
    }
}