package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.thermobox.restserver.security.scope
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.Response
import java.time.LocalDateTime

@Path("api/v2/temperatures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TemperatureResource {

    private val service = BusinessFactory.getTemperatureService()
    private val accessService = BusinessFactory.getAccessService()

    @Context
    private lateinit var security: SecurityContext

    // Temperatures of the rooms / devices the user may see only (all of them for unlimited users).
    private val access by lazy { security.scope }

    private fun allowed(list: List<Temperature>): List<Temperature> = accessService.filterTemperatures(access, list)

    /** The latest temperatures first, a page at a time (?limit=1000&offset=0, see [Page]). */
    @GET
    @RequiresAuth(roles = ["ADMIN", "MANAGER", "VIEWER"])
    fun getAll(@QueryParam("limit") limit: Int?, @QueryParam("offset") offset: Int?): Response {
        val page = Page.of(limit, offset) ?: return Page.invalid()
        return paged(null, page)
    }

    @GET
    @Path("/{id}")
    @RequiresAuth(roles = ["ADMIN", "MANAGER", "VIEWER"])
    fun getById(@PathParam("id") id: Long): Temperature? {
        return service.getById(id)?.let { allowed(listOf(it)).firstOrNull() }
    }

    @GET
    @Path("/type/{type}")
    @RequiresAuth(roles = ["ADMIN", "MANAGER", "VIEWER"])
    fun getByType(
        @PathParam("type") type: String,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response {
        val sample = resolveSample(type) ?: return Response.noContent().build()
        val page = Page.of(limit, offset) ?: return Page.invalid()
        return paged(sample, page)
    }

    /**
     * A page of the temperatures of [sample]'s kind (all kinds when null). A limited user gets
     * those of their page they may see. The total (a count over the whole table, about a second
     * for millions of rows) is given with the first page only, and to unlimited users only.
     */
    private fun paged(sample: Temperature?, page: Page): Response {
        val list = allowed(service.getPage(sample, page.offset, page.limit))
        val response = Response.ok(list)
        if (access.all && page.offset == 0) response.header(Page.TOTAL_HEADER, service.count(sample))
        return response.build()
    }

    @GET
    @Path("/average/{type}")
    @RequiresAuth(roles = ["ADMIN", "MANAGER", "VIEWER"])
    fun getAverage(@PathParam("type") type: String): Temperature? {
        val sample = resolveSample(type) ?: return null
        if (access.all) return service.getAverage(sample)
        // Limited user: the average over their temperatures only.
        val values = allowed(service.getByType(sample).orEmpty()).mapNotNull { it.value }
        if (values.isEmpty()) return null
        return sample.apply { value = values.average().toFloat() }
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
     * Adds a measurement ("measurement") or a setpoint ("setpoint"): {"value": 21.5, "date": …}.
     * The plain POST above can't say which one it is.
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
        // Changes the stored row, so a setpoint stays a setpoint and a measurement a measurement.
        val existing = service.getById(id)
        if (existing == null) {
            temperature.id = id
            service.update(temperature)
            return
        }
        temperature.value?.let { existing.value = it }
        temperature.date?.let { existing.date = it }
        service.update(existing)
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
            "setpoint" -> ax.ibr.thermobox.common.entities.Setpoint(0f, LocalDateTime.now())
            "measurement" -> ax.ibr.thermobox.common.entities.Measurement(0f, LocalDateTime.now())
            else -> null
        }
    }
}