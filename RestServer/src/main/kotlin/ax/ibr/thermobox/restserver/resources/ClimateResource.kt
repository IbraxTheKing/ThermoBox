package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.charts.CurveSampling
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.implementations.ClimateServiceImpl
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.SetpointRequest
import ax.ibr.thermobox.restserver.dto.SetpointDispatchDto
import ax.ibr.thermobox.restserver.dto.HistoryDto
import ax.ibr.thermobox.restserver.dto.PointDto
import ax.ibr.thermobox.restserver.dto.SeriesDto
import ax.ibr.thermobox.restserver.hierarchy.UserView
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.scope
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Temperatures at the level of any element of the hierarchy (ClimateService): a room, a floor or
 * a building aggregates the devices it contains, and follows the setpoint of its nearest ancestor
 * when it has none of its own. Only what the current user may see.
 *
 * Periods are given as ISO-8601 instants: ?start=2026-10-20T08:00:00Z&end=2026-10-21T08:00:00Z.
 *
 * Curves can be long (a device measuring every minute gives 43 200 points a month): each
 * measurement curve is reduced to at most ?points=N of its points (1000 by default), chosen to
 * draw almost the same line ([CurveSampling]). A chart needs about as many points as it is wide
 * in pixels. Past two days, the curves are drawn from hourly averages (ClimateService); points=0
 * gives every measurement, whatever the period. Setpoints are never reduced: they change rarely
 * and every change matters.
 */
@Path("api/v2/climate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ClimateResource {

    private val climate = BusinessFactory.getClimateService()
    private val hierarchy = BusinessFactory.getHierarchyService()

    @Context
    private lateinit var security: SecurityContext

    private val view by lazy { UserView(security.currentUser, security.scope) }

    /**
     * Curves of an element over the period: one per device for a room (MANAGER, ADMIN), one
     * per room for a floor or a building, their average, and the setpoints.
     */
    @GET
    @Path("/{id}/history")
    fun history(
        @PathParam("id") id: Long,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?,
        @QueryParam("points") points: Int?,
    ): Response {
        val s = view.element(id) ?: return notFound()
        val (de, a) = period(start, end) ?: return error("start and end must be ISO-8601 instants, start before end")
        if (!validPoints(points)) return error(POINTS_ERROR)
        fun curve(c: List<Temperature>) = reduce(c, points).map { PointDto.of(it) }

        // points=0: every measurement; otherwise hourly averages past two days (ClimateService)
        val histories = if (points == 0) climate.getRawHistories(s, de, a) else climate.getHistories(s, de, a)
        val setpoints = climate.getSetpoints(s, de, a).map { PointDto.of(it) }
        val average = curve(ClimateServiceImpl.movingAverage(histories))
        val container = s.kind == RoomKind.FLOOR || s.kind == RoomKind.BUILDING

        val dto = when {
            container -> HistoryDto(
                devices = emptyList(),
                // One curve per room (average of its devices) rather than one per device.
                rooms = hierarchy.getRooms(s).map { room ->
                    val ids = hierarchy.getDevices(room).mapNotNull { it.id }.toSet()
                    SeriesDto(room.id, room.name, curve(ClimateServiceImpl.movingAverage(histories.filter { it.device.id in ids })))
                }.filter { it.points.isNotEmpty() },
                average = average,
                setpoints = setpoints,
            )
            // VIEWER: the temperature of the room, not one curve per device.
            !view.devicesVisible -> HistoryDto(emptyList(), emptyList(), average, setpoints)
            else -> HistoryDto(
                devices = histories.map { h -> SeriesDto(h.device.id, h.device.name, curve(h.measurements)) },
                rooms = emptyList(),
                average = average,
                setpoints = setpoints,
            )
        }
        return Response.ok(dto).build()
    }

    /**
     * Average curve of several elements at once (dashboard sparklines, measurements page):
     * ?ids=3,7,12&start=…&end=… gives {"3": [points…], "7": […]}. Elements the user may not see
     * are left out. With &points=N, each curve has at most N points (sparklines need few).
     */
    @GET
    @Path("/averages")
    fun averages(
        @QueryParam("ids") ids: String?,
        @QueryParam("start") start: String?,
        @QueryParam("end") end: String?,
        @QueryParam("points") points: Int?,
    ): Response {
        val (de, a) = period(start, end) ?: return error("start and end must be ISO-8601 instants, start before end")
        if (!validPoints(points)) return error(POINTS_ERROR)
        val requested = ids.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
        if (requested.size > MAX_ELEMENTS) return error("At most $MAX_ELEMENTS ids per request")
        val result = LinkedHashMap<String, List<PointDto>>()
        for (id in requested) {
            val s = view.element(id) ?: continue
            val curve = if (points == 0) ClimateServiceImpl.movingAverage(climate.getRawHistories(s, de, a)) else climate.getAverageHistory(s, de, a)
            result[id.toString()] = reduce(curve, points).map { PointDto.of(it) }
        }
        return Response.ok(result).build()
    }

    /**
     * {"value": 21.5, "force": false}: saves the setpoint (Celsius) of a building, a floor or a
     * room and sends it to the devices below. Those below with their own setpoint keep it unless
     * "force" is true.
     */
    @PUT
    @Path("/{id}/setpoint")
    @RequiresAuth(roles = ["ADMIN", "MANAGER"], allowOwner = false)
    fun sendSetpoint(@PathParam("id") id: Long, request: SetpointRequest?): Response {
        val s = view.element(id) ?: return notFound()
        if (s is Device) return error("A setpoint is set on a building, a floor or a room")
        if (request == null) return error("Missing body")
        val value = request.value ?: return error("value is required")
        return try {
            Response.ok(SetpointDispatchDto.of(climate.sendSetpoint(s, value, request.force))).build()
        } catch (e: Exception) {
            // Refused value (too hot, impossible): nothing was saved.
            error(e.message ?: "Setpoint refused")
        }
    }

    private fun period(start: String?, end: String?): Pair<Instant, Instant>? = try {
        val de = Instant.parse(start ?: return null)
        val a = Instant.parse(end ?: return null)
        if (de.isBefore(a)) de to a else null
    } catch (e: DateTimeParseException) {
        null
    }

    private fun validPoints(points: Int?): Boolean = points == null || points == 0 || points in CurveSampling.MIN_POINTS..MAX_POINTS

    /** [curve] reduced to at most [points] points ([DEFAULT_POINTS] when not given), as it is for 0. */
    private fun reduce(curve: List<Temperature>, points: Int?): List<Temperature> = when (points) {
        0 -> curve
        else -> CurveSampling.sample(curve, points ?: DEFAULT_POINTS)
    }

    private fun notFound(): Response = Response.status(Response.Status.NOT_FOUND).build()

    private fun error(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

    private companion object {
        const val MAX_ELEMENTS = 200
        const val MAX_POINTS = 10_000
        /** Without ?points: enough for a chart as wide as a screen, a bounded response in any case. */
        const val DEFAULT_POINTS = 1000
        val POINTS_ERROR = "points must be 0 (every point) or between ${CurveSampling.MIN_POINTS} and $MAX_POINTS"
    }
}
