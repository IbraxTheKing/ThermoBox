package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import ax.ibr.thermobox.restserver.audit.Audited
import ax.ibr.utils.rest.RequiresRole
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * The log of the changes made through the API (ADMIN, ThermoBox 26.12): who changed what and
 * when (setpoints, buildings and rooms, users, groups, alerts, settings of the server…), whoever
 * made it, ADMIN or MANAGER. Written by [ax.ibr.thermobox.restserver.filters.AuditFilter].
 *
 * GET /api/v2/audit?offset=0&limit=50&user=3&action=rule&target=element&targetId=12&from=…&to=…&q=salle
 */
@Path("api/v2/audit")
@Produces(MediaType.APPLICATION_JSON)
class AuditResource {

    private val log get() = BusinessFactory.getAuditLog()

    /**
     * {"total": 1234, "entries": [...]}: the most recent first. action: one (« rule.create ») or a
     * domain (« rule »); from / to: ISO dates (2026-12-01T00:00:00, or with an offset); q: in the
     * user, the target or the values.
     */
    @GET
    @RequiresRole("ADMIN")
    fun list(
        @QueryParam("offset") offset: Int?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("user") user: Long?,
        @QueryParam("action") action: String?,
        @QueryParam("target") target: String?,
        @QueryParam("targetId") targetId: Long?,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
        @QueryParam("q") q: String?,
    ): Response {
        val l = limit ?: 50
        val o = offset ?: 0
        if (l !in 1..500 || o < 0) return bad("limit must be between 1 and 500, offset at least 0")
        val start = date(from) ?: if (from.isNullOrBlank()) null else return bad("from: an ISO date")
        val end = date(to) ?: if (to.isNullOrBlank()) null else return bad("to: an ISO date")
        val query = AuditQuery(user, action?.trim()?.takeIf { it.isNotEmpty() }, target?.trim()?.takeIf { it.isNotEmpty() }, targetId, start, end, q?.take(200), o, l)
        return Response.ok(AuditPageDto(log.count(query), log.find(query).map { AuditEntryDto.of(it) })).build()
    }

    /** The users found in the log, for its filter: [{"id": 3, "username": "alice"}]. */
    @GET
    @Path("/users")
    @RequiresRole("ADMIN")
    fun users(): List<AuditUserDto> = log.users().map { (id, name) -> AuditUserDto(id, name) }

    /** {"retentionDays": 90}; null: kept for good. */
    @GET
    @Path("/settings")
    @RequiresRole("ADMIN")
    fun settings(): AuditSettingsDto = AuditSettingsDto(log.retentionDays())

    /** {"retentionDays": 365} (1 to 3650), or null to keep everything; what is older goes at once. */
    @PUT
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRole("ADMIN")
    @Audited("audit.retention")
    fun setSettings(request: AuditSettingsDto?): Response = try {
        Response.ok(AuditSettingsDto(log.setRetention(request?.retentionDays))).build()
    } catch (e: IllegalArgumentException) {
        bad(e.message ?: "Invalid retention")
    } catch (e: Exception) {
        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(mapOf("error" to "The data folder is not writable: ${e.message}")).build()
    }

    private fun date(s: String?): LocalDateTime? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDateTime.parse(t)
        } catch (e: DateTimeParseException) {
            runCatching { OffsetDateTime.parse(t).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
        }
    }

    private fun bad(message: String): Response = Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()
}

data class AuditPageDto(val total: Long, val entries: List<AuditEntryDto>)

data class AuditUserDto(val id: Long?, val username: String)

class AuditSettingsDto() {
    var retentionDays: Int? = null

    constructor(days: Int?) : this() {
        retentionDays = days
    }
}

/** [details]: the values sent ("changes", secrets removed), what they replaced ("before"), where ("place"). */
data class AuditEntryDto(
    val id: Long,
    val at: String,
    val userId: Long?,
    val username: String,
    val role: String?,
    val action: String,
    val targetType: String?,
    val targetId: Long?,
    val targetName: String?,
    val details: JsonNode?,
) {
    companion object {
        private val json = ObjectMapper()

        fun of(e: AuditEntry) = AuditEntryDto(
            e.id ?: 0, e.at.toString(), e.userId, e.username, e.role, e.action, e.targetType, e.targetId, e.targetName,
            e.details?.let { runCatching { json.readTree(it) }.getOrNull() },
        )
    }
}
