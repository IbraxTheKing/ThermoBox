package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.restserver.audit.Audited
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.restserver.dto.AlertChannelDto
import ax.ibr.thermobox.restserver.dto.AlertChannelRequest
import ax.ibr.thermobox.restserver.dto.AlertDeliveryDto
import ax.ibr.thermobox.restserver.dto.AlertEventDto
import ax.ibr.thermobox.restserver.dto.AlertOverviewDto
import ax.ibr.thermobox.restserver.dto.AlertRuleDto
import ax.ibr.thermobox.restserver.dto.AlertRuleRequest
import ax.ibr.thermobox.restserver.dto.EnabledRequest
import ax.ibr.thermobox.restserver.dto.MonitorStatusDto
import ax.ibr.thermobox.restserver.dto.RuleRoomDto
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * Temperature alerts (ADMIN only, 26.12): the channels that reach people (email, webhooks), the
 * rules that say when, the history of the excesses and of the messages. The server watches the
 * rooms by itself (AlertMonitor), whether a client is connected or not.
 *
 * Secrets are write-only: no answer contains an SMTP password, a webhook URL or a token.
 */
@Path("api/v2/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AlertResource {

    private val alerts = BusinessFactory.getAlertService()

    /** Everything a page of alerts shows at once: state of the monitor, channels, rules, alerts in progress, last failures. */
    @GET
    @RequiresRole("ADMIN")
    fun overview(): AlertOverviewDto {
        val o = alerts.overview()
        val rule = ruleDto(o.channels.associate { it.channel.id!! to (it.channel.name to it.channel.kind.name) })
        return AlertOverviewDto.of(o, rule)
    }

    @GET
    @Path("/status")
    @RequiresRole("ADMIN")
    fun status() = MonitorStatusDto.of(alerts.status())

    // ------------------------------------------------------------------ channels

    @GET
    @Path("/channels")
    @RequiresRole("ADMIN")
    fun channels(): List<AlertChannelDto> = alerts.channels().map { AlertChannelDto.of(it) }

    @GET
    @Path("/channels/{id}")
    @RequiresRole("ADMIN")
    fun channel(@PathParam("id") id: Long): Response =
        alerts.channel(id)?.let { Response.ok(AlertChannelDto.of(it)).build() } ?: notFound()

    @POST
    @Path("/channels")
    @RequiresRole("ADMIN")
    @Audited("channel.create", target = "channel")
    fun createChannel(request: AlertChannelRequest?): Response = valid {
        val r = request ?: return@valid error("Missing body")
        Response.status(Response.Status.CREATED).entity(AlertChannelDto.of(alerts.createChannel(r.toInput(null)))).build()
    }

    @PUT
    @Path("/channels/{id}")
    @RequiresRole("ADMIN")
    @Audited("channel.update", target = "channel")
    fun updateChannel(@PathParam("id") id: Long, request: AlertChannelRequest?): Response = valid {
        val r = request ?: return@valid error("Missing body")
        val current = alerts.channel(id) ?: return@valid notFound()
        alerts.updateChannel(id, r.toInput(current.channel.kind))?.let { Response.ok(AlertChannelDto.of(it)).build() } ?: notFound()
    }

    /** {"enabled": false}: on or off, nothing else changed. */
    @PUT
    @Path("/channels/{id}/enabled")
    @RequiresRole("ADMIN")
    @Audited("channel.enable", target = "channel")
    fun enableChannel(@PathParam("id") id: Long, request: EnabledRequest?): Response {
        val enabled = request?.enabled ?: return error("enabled: true or false")
        return alerts.enableChannel(id, enabled)?.let { Response.ok(AlertChannelDto.of(it)).build() } ?: notFound()
    }

    @DELETE
    @Path("/channels/{id}")
    @RequiresRole("ADMIN")
    @Audited("channel.delete", target = "channel")
    fun deleteChannel(@PathParam("id") id: Long): Response =
        if (alerts.deleteChannel(id)) Response.noContent().build() else notFound()

    /** Sends a test message now; the answer is the delivery: SENT, or FAILED with the error. */
    @POST
    @Path("/channels/{id}/test")
    @RequiresRole("ADMIN")
    fun test(@PathParam("id") id: Long): Response =
        alerts.test(id)?.let { Response.ok(AlertDeliveryDto.of(it)).build() } ?: notFound()

    // ------------------------------------------------------------------ rules

    @GET
    @Path("/rules")
    @RequiresRole("ADMIN")
    fun rules(): List<AlertRuleDto> = alerts.rules().map(ruleDto())

    @GET
    @Path("/rules/{id}")
    @RequiresRole("ADMIN")
    fun rule(@PathParam("id") id: Long): Response =
        alerts.rule(id)?.let { Response.ok(ruleDto()(it)).build() } ?: notFound()

    @POST
    @Path("/rules")
    @RequiresRole("ADMIN")
    @Audited("rule.create", target = "rule")
    fun createRule(request: AlertRuleRequest?): Response = valid {
        val r = request ?: return@valid error("Missing body")
        Response.status(Response.Status.CREATED).entity(ruleDto()(alerts.createRule(r.toRule()))).build()
    }

    @PUT
    @Path("/rules/{id}")
    @RequiresRole("ADMIN")
    @Audited("rule.update", target = "rule")
    fun updateRule(@PathParam("id") id: Long, request: AlertRuleRequest?): Response = valid {
        val r = request ?: return@valid error("Missing body")
        alerts.updateRule(id, r.toRule())?.let { Response.ok(ruleDto()(it)).build() } ?: notFound()
    }

    @PUT
    @Path("/rules/{id}/enabled")
    @RequiresRole("ADMIN")
    @Audited("rule.enable", target = "rule")
    fun enableRule(@PathParam("id") id: Long, request: EnabledRequest?): Response {
        val enabled = request?.enabled ?: return error("enabled: true or false")
        return alerts.enableRule(id, enabled)?.let { Response.ok(ruleDto()(it)).build() } ?: notFound()
    }

    /**
     * The rooms where the rule applies (those where no nearer rule replaces it), with their
     * temperature against its threshold and their alert in progress; in the order of the tree.
     */
    @GET
    @Path("/rules/{id}/rooms")
    @RequiresRole("ADMIN")
    fun ruleRooms(@PathParam("id") id: Long): Response =
        alerts.roomsOf(id)?.let { rooms -> Response.ok(rooms.map { RuleRoomDto.of(it) }).build() } ?: notFound()

    @DELETE
    @Path("/rules/{id}")
    @RequiresRole("ADMIN")
    @Audited("rule.delete", target = "rule")
    fun deleteRule(@PathParam("id") id: Long): Response =
        if (alerts.deleteRule(id)) Response.noContent().build() else notFound()

    // ------------------------------------------------------------------ history

    /**
     * ?state=ACTIVE|RESOLVED|CANCELLED&roomId=…&ruleId=…&limit=50&before=<id>: newest first; the
     * next page with before = the id of the last one.
     */
    @GET
    @Path("/events")
    @RequiresRole("ADMIN")
    fun events(
        @QueryParam("state") state: String?,
        @QueryParam("roomId") roomId: Long?,
        @QueryParam("ruleId") ruleId: Long?,
        @QueryParam("before") before: Long?,
        @QueryParam("limit") limit: Int?,
    ): Response {
        val s = state?.takeIf { it.isNotBlank() }?.let { v -> AlertState.entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: return error("state: ACTIVE, RESOLVED or CANCELLED") }
        return Response.ok(alerts.events(s, roomId, ruleId, before, (limit ?: 50).coerceIn(1, 500)).map { AlertEventDto.of(it) }).build()
    }

    /** One alert and every message sent about it. */
    @GET
    @Path("/events/{id}")
    @RequiresRole("ADMIN")
    fun event(@PathParam("id") id: Long): Response =
        alerts.event(id)?.let { Response.ok(AlertEventDto.of(it)).build() } ?: notFound()

    /** ?status=PENDING|SENT|FAILED&limit=100: the messages, newest first. */
    @GET
    @Path("/deliveries")
    @RequiresRole("ADMIN")
    fun deliveries(@QueryParam("status") status: String?, @QueryParam("limit") limit: Int?): Response {
        val s = status?.takeIf { it.isNotBlank() }?.let { v -> DeliveryStatus.entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: return error("status: PENDING, SENT or FAILED") }
        return Response.ok(alerts.deliveries(s, (limit ?: 100).coerceIn(1, 500)).map { AlertDeliveryDto.of(it) }).build()
    }

    // ------------------------------------------------------------------

    private fun ruleDto(channels: Map<Long, Pair<String, String>> = alerts.channels().associate { it.channel.id!! to (it.channel.name to it.channel.kind.name) }): (AlertRule) -> AlertRuleDto {
        val elements = BusinessFactory.getHierarchyCache().tree().elements
        return { r -> AlertRuleDto.of(r, elements, channels) }
    }

    private inline fun valid(block: () -> Response): Response = try {
        block()
    } catch (e: IllegalArgumentException) {
        error(e.message ?: "Invalid request")
    }

    private fun notFound(): Response = Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to "Not found")).build()

    private fun error(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}
