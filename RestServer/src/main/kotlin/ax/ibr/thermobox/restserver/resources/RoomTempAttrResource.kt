package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.rules.SetpointRules
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.thermobox.restserver.security.scope
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.time.LocalDateTime

@Path("api/v2/roomtemps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class RoomTempAttrResource {

    private val service = BusinessFactory.getRoomTempAttrService()
    private val roomService = BusinessFactory.getRoomService()

    private val requestBoxes: ProtocolDriver = BusinessFactory.getDriver()

    @Context
    private lateinit var security: SecurityContext

    // One resource instance per request: the user's accesses are worked out once per request.
    private val access by lazy { security.scope }

    /** The room [id] if it exists and the user may see it; otherwise null, as for an unknown id. */
    private fun room(id: Long) = roomService.getById(id)?.takeIf { access.canSee(it) }

    /** The latest first, a page at a time (?limit=1000&offset=0, see [Page]). */
    @GET
    fun getAll(@QueryParam("limit") limit: Int?, @QueryParam("offset") offset: Int?): Response {
        val page = Page.of(limit, offset) ?: return Page.invalid()
        return Response.ok(service.getPage(page.offset, page.limit).filter { access.canSee(it.room) }).build()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): RoomTempAttr? {
        return service.getById(id)?.takeIf { access.canSee(it.room) }
    }

    @GET
    @Path("/room/{id}")
    fun getByRoomId(@PathParam("id") id: Long): List<RoomTempAttr>? {
        if (!access.canSee(id)) return null
        return service.getByRoom(id)
    }

    @GET
    @Path("/temperature/{id}")
    fun getByTemperatureId(@PathParam("id") id: Long): RoomTempAttr? {
        return service.getByTemperature(id)?.takeIf { access.canSee(it.room) }
    }

    @GET
    @Path("/room/{id}/current")
    fun getCurrentTemperaturesFromRoom(@PathParam("id") id: Long): List<Temperature>? {
        val room = room(id) ?: return null
        return service.getCurrentTemperaturesFromRoom(room)
    }

    @GET
    @Path("/room/{id}/setpoint")
    fun getSetpointFromRoom(@PathParam("id") id: Long): Setpoint? {
        val room = room(id) ?: return null
        return service.getSetpointFromRoom(room)
    }

    @GET
    @Path("/room/{id}/temperatures")
    fun getTemperaturesFromRoomTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Temperature>? {
        val room = room(id) ?: return null
        return service.getTemperaturesFromRoomTimed(room, instant(start, "start"), instant(end, "end"))
    }

    @GET
    @Path("/room/{id}/setpoints")
    fun getSetpointsFromRoomTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Setpoint>? {
        val room = room(id) ?: return null
        return service.getSetpointsFromRoomTimed(room, instant(start, "start"), instant(end, "end"))
    }

    @GET
    @Path("/room/{id}/average")
    fun getAverageTemperatureFromRoomTimed(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?
    ): List<Temperature>? {
        val room = room(id) ?: return null
        return service.getAverageTemperatureFromRoomTimed(room, instant(start, "start"), instant(end, "end"))
    }

    /** ?start= / ?end= required: missing, it was a NullPointerException (500). */
    private fun instant(value: String?, name: String): Instant =
        Instant.parse(requireNotNull(value?.takeIf { it.isNotBlank() }) { "$name is required (ISO-8601 instant)" })

    @POST
    @RequiresAuth(roles = ["ADMIN", "MANAGER"], allowOwner = false)
    fun add(roomTempAttr: RoomTempAttr): Response {
        if (!access.canSee(roomTempAttr.room)) return Response.status(Response.Status.NOT_FOUND).build()
        return try {
            service.add(roomTempAttr)
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
    @RequiresAuth(roles = ["ADMIN", "MANAGER"], allowOwner = false)
    fun updateById(@PathParam("id") id: Long, roomTempAttr: RoomTempAttr): Response {
        // Both the current room and the new one must be allowed.
        val current = service.getById(id)?.takeIf { access.canSee(it.room) }
        if (current == null || !access.canSee(roomTempAttr.room)) return Response.status(Response.Status.NOT_FOUND).build()
        roomTempAttr.id = id
        service.update(roomTempAttr)
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
    @RequiresAuth(roles = ["ADMIN", "MANAGER"], allowOwner = false)
    @Path("/room/{id}/setpoint")
    fun sendSetpoint(
        @PathParam("id") id: Long,
        @QueryParam("value") value: Float?
    ): Response {
        val room = room(id)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        // Without ?value=, a Float parameter was 0: a setpoint of 0 °C was saved
        if (value == null) return refused("value is required")
        // Checked before saving: the check of the driver came after, and its refusal was only
        // printed, so a refused setpoint (too hot, too low, NaN) was kept and the answer was 200
        try {
            SetpointRules.check(value)
        } catch (e: Exception) {
            return refused(e.message ?: "Setpoint refused")
        }

        val setpoint = Setpoint(value, java.time.LocalDateTime.now())

        return try {
            service.add(RoomTempAttr(room, setpoint))

            if (requestBoxes != null) {
                try {
                    requestBoxes.sendSetpoint(room, setpoint)
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

    private fun refused(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

    @GET
    @Path("/room/{id}/measured/latest")
    fun getLatestMeasured(@PathParam("id") id: Long): Temperature? {
        val room = room(id) ?: return null
        return service.getCurrentTemperatureFromRoom(room, Measurement(0f, LocalDateTime.now()))
    }
}