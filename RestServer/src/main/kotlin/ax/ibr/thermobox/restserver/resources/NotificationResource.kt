package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.restserver.dto.SetpointEventDto
import ax.ibr.thermobox.restserver.dto.SetpointEventsDto
import ax.ibr.thermobox.restserver.dto.iso
import ax.ibr.thermobox.restserver.hierarchy.UserView
import ax.ibr.thermobox.restserver.notifications.SetpointWatch
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.scope
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * What the apps notify (ThermoBox 26.12): the rooms that started or stopped respecting their
 * setpoint ([SetpointWatch]).
 *
 * GET /api/v2/notifications/setpoints?since=2026-12-01T08:00:00&ids=3,7,12
 *
 * - since: the [SetpointEventsDto.now] of the previous answer (a local date-time of the server,
 *   or an ISO-8601 instant); 15 minutes ago without it; never more than [MAX_BACK] ago (an app
 *   closed for days does not get days of changes).
 * - ids: the rooms to watch (a building or a floor: the rooms under it); every room the user may
 *   see without it.
 *
 * Read from the measures on each request, nothing stored: any user, any app, any time.
 */
@Path("api/v2/notifications")
@Produces(MediaType.APPLICATION_JSON)
class NotificationResource {

    private val climate = BusinessFactory.getClimateService()

    @Context
    private lateinit var security: SecurityContext

    private val view by lazy { UserView(security.currentUser, security.scope) }

    @GET
    @Path("/setpoints")
    fun setpoints(@QueryParam("since") since: String?, @QueryParam("ids") ids: String?): Response {
        val now = LocalDateTime.now()
        val asked = if (since.isNullOrBlank()) now.minus(DEFAULT_BACK) else parse(since) ?: return error("since must be a date-time (ISO-8601)")
        val oldest = now.minus(MAX_BACK)
        val from = when {
            asked.isBefore(oldest) -> oldest
            asked.isAfter(now) -> now
            else -> asked
        }
        val requested = ids?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.distinct()
        if (requested != null && requested.size > MAX_IDS) return error("At most $MAX_IDS ids per request")

        val rooms = view.watchedRooms(requested)
        val zone = ZoneId.systemDefault()
        val start = from.minus(SetpointWatch.LOOKBACK).atZone(zone).toInstant()
        val end = now.atZone(zone).toInstant()
        val events = ArrayList<SetpointEventDto>()
        // A few rooms per query, as /climate/averages
        for (part in rooms.chunked(ROOMS_PER_QUERY)) {
            val curves = climate.getAverageHistories(part.map { it.first }, start, end, raw = true)
            for ((room, setpoint) in part) {
                val curve = curves[room.id].orEmpty()
                for (c in SetpointWatch.changes(curve, setpoint, from, now)) {
                    events += SetpointEventDto(room.id, room.name, view.placeOf(room), c.state.name, c.previous.name, c.temperature, setpoint, iso(c.at))
                }
            }
        }
        events.sortBy { it.at }
        return Response.ok(SetpointEventsDto(iso(from), iso(now), events)).build()
    }

    private fun parse(s: String): LocalDateTime? = try {
        LocalDateTime.parse(s)
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault())
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun error(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

    private companion object {
        val DEFAULT_BACK: Duration = Duration.ofMinutes(15)
        val MAX_BACK: Duration = Duration.ofHours(6)
        const val MAX_IDS = 500
        const val ROOMS_PER_QUERY = 3
    }
}
