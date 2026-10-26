package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.settings.Range
import ax.ibr.thermobox.business.settings.TemperatureLimits
import ax.ibr.thermobox.business.settings.TemperatureLimitsStore
import ax.ibr.thermobox.business.update.BuildInfo
import ax.ibr.thermobox.restserver.branding.Logo
import ax.ibr.thermobox.restserver.branding.LogoImages
import ax.ibr.thermobox.restserver.branding.LogoSource
import ax.ibr.thermobox.restserver.branding.ServerLogo
import ax.ibr.utils.rest.PublicEndpoint
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * The ThermoBox server itself: its name, version and logo, for the clients to show (login page,
 * about page, browser tab icon). Reading them is public: a client needs them before anyone logs in.
 *
 * The logo is the one an ADMIN gave (PUT), otherwise the logo of the website the server belongs
 * to (`thermobox.example.com` shows the logo of `example.com`), otherwise the ThermoBox logo.
 */
@Path("api/v2/server")
class ServerResource {

    @Context
    private lateinit var headers: HttpHeaders

    @Context
    private lateinit var uriInfo: UriInfo

    private val logos get() = ServerLogo.instance

    /** {"name": "ThermoBox", "version": "26.12.0", "logo": "/api/v2/server/logo?v=…", "logoSource": "site"} */
    @GET
    @PublicEndpoint
    @Produces(MediaType.APPLICATION_JSON)
    fun info(): ServerInfoDto = ServerInfoDto.of(logos.current(host()))

    /**
     * The logo in its own format (the ThermoBox logo is an SVG). With `?size=N`, a PNG whose
     * largest side is at most N pixels when the logo can be resized (PNG, JPEG, GIF, and the
     * ThermoBox logo); an SVG, ICO or WebP logo is always served as it is. `?v=` (given by
     * GET /api/v2/server) only makes the URL change with the logo.
     */
    @GET
    @Path("/logo")
    @PublicEndpoint
    fun logo(
        @QueryParam("size") size: Int?,
        @QueryParam("v") v: String?,
        @HeaderParam("If-None-Match") clientEtag: String?,
    ): Response {
        val current = logos.current(host())
        val served = if (size != null && size > 0) resized(current, size) else current
        val etag = "\"${served.version}\""
        val response = if (clientEtag == etag) Response.notModified() else Response.ok(served.bytes, served.type)
        response
            .header("ETag", etag)
            // With the version in the URL, the image under that URL never changes
            .header("Cache-Control", if (v != null && v == current.version) "public, max-age=604800" else "public, max-age=300")
            .header("Vary", "Host, X-Forwarded-Host")
            .header("X-Content-Type-Options", "nosniff")
        // An SVG opened on its own runs nothing and loads nothing
        if (served.isSvg) response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox")
        return response.build()
    }

    /**
     * Replaces the logo (ADMIN): the body is the image, PNG, JPEG or GIF (kept as a PNG of at most
     * 1024 pixels), or SVG (without scripts), 5 MB at most.
     */
    @PUT
    @Path("/logo")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("ADMIN")
    fun putLogo(data: ByteArray?): Response = try {
        logos.setCustom(data ?: ByteArray(0))
        Response.ok(info()).build()
    } catch (e: IllegalArgumentException) {
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to e.message)).build()
    } catch (e: Exception) {
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(mapOf("error" to "The data folder is not writable: ${e.message}")).build()
    }

    /** Back to the automatic logo: the website's, otherwise ThermoBox's (ADMIN). */
    @DELETE
    @Path("/logo")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("ADMIN")
    fun deleteLogo(): Response {
        logos.deleteCustom()
        return Response.ok(info()).build()
    }

    /**
     * Temperatures the server accepts (Celsius): readings of the devices or added by hand
     * outside [measurement] are refused (a device's are dropped as sensor errors), and so are
     * setpoints outside [setpoint]. Every user may read them, to bound their inputs.
     */
    @GET
    @Path("/limits")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuth
    fun limits(): TemperatureLimitsDto = TemperatureLimitsDto.of(TemperatureLimitsStore.instance.get())

    /** {"measurement": {"min": -10, "max": 60}, "setpoint": {"min": 5, "max": 35}}; a missing part is kept (ADMIN). */
    @PUT
    @Path("/limits")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("ADMIN")
    fun setLimits(request: TemperatureLimitsDto?): Response {
        val current = TemperatureLimitsStore.instance.get()
        return try {
            val limits = TemperatureLimits(
                request?.measurement?.toRange(current.measurement) ?: current.measurement,
                request?.setpoint?.toRange(current.setpoint) ?: current.setpoint,
            )
            TemperatureLimitsStore.instance.set(limits)
            Response.ok(TemperatureLimitsDto.of(limits)).build()
        } catch (e: IllegalArgumentException) {
            Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to e.message)).build()
        } catch (e: Exception) {
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "The data folder is not writable: ${e.message}")).build()
        }
    }

    /** Back to the default limits (-10 to 50 °C for both) (ADMIN). */
    @DELETE
    @Path("/limits")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("ADMIN")
    fun resetLimits(): TemperatureLimitsDto {
        TemperatureLimitsStore.instance.reset()
        return TemperatureLimitsDto.of(TemperatureLimitsStore.instance.get())
    }

    /** The name the client used to reach the server, behind a reverse proxy too (without the port). */
    private fun host(): String? {
        val raw = headers.getHeaderString("X-Forwarded-Host")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: headers.getHeaderString(HttpHeaders.HOST)
            ?: uriInfo.requestUri.host
        return raw?.substringBefore(':')
    }

    private fun resized(logo: Logo, size: Int): Logo = when {
        logo.source == LogoSource.DEFAULT ->
            ServerLogo.DEFAULT_PNG.entries.firstOrNull { it.key >= size }?.value ?: ServerLogo.DEFAULT_PNG.getValue(ServerLogo.DEFAULT_PNG_SIZES.last())
        LogoImages.isRaster(logo.type) -> {
            if (RESIZED.size > 64) RESIZED.clear()
            RESIZED.computeIfAbsent("${logo.version}-$size") {
                runCatching { Logo(LogoImages.toPng(logo.bytes, minOf(size, LogoImages.MAX_SIDE)), LogoImages.PNG, logo.source) }.getOrDefault(logo)
            }
        }
        else -> logo
    }

    private companion object {
        val RESIZED = ConcurrentHashMap<String, Logo>()
    }
}

data class ServerInfoDto(val name: String, val version: String, val logo: String, val logoSource: String) {
    companion object {
        fun of(logo: Logo) = ServerInfoDto("ThermoBox", BuildInfo.text, "/api/v2/server/logo?v=${logo.version}", logo.source.name.lowercase())
    }
}

/** A range in JSON; a missing bound keeps its current value. */
class RangeDto {
    var min: Float? = null
    var max: Float? = null

    fun toRange(current: Range) = Range(min ?: current.min, max ?: current.max)

    companion object {
        fun of(r: Range) = RangeDto().apply { min = r.min; max = r.max }
    }
}

class TemperatureLimitsDto {
    var measurement: RangeDto? = null
    var setpoint: RangeDto? = null

    companion object {
        fun of(l: TemperatureLimits) = TemperatureLimitsDto().apply {
            measurement = RangeDto.of(l.measurement)
            setpoint = RangeDto.of(l.setpoint)
        }
    }
}
