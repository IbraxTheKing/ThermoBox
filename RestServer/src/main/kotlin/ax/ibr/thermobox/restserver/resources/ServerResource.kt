package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.update.BuildInfo
import ax.ibr.utils.rest.PublicEndpoint
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

/**
 * The ThermoBox server itself, for a client to know what it talks to before anyone logs in:
 * `GET /api/server` → `{"name": "ThermoBox", "version": "26.11.1", "apiVersions": ["v1"]}`.
 *
 * The same answer (same keys, more of them later) is given from 26.12 on, where [apiVersions]
 * also lists "v2" (the English API under /api/v2). A server older than 26.11 answers 404.
 */
@Path("api/server")
@PublicEndpoint
class ServerResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun info(): ServerInfoDto = ServerInfoDto.current()
}

/**
 * [version]: tag of the release ("26.11.1", "26.11.0-rc.2"), or "dev" for a build without a version.
 * [apiVersions]: the REST APIs this server answers, oldest first.
 */
data class ServerInfoDto(val name: String, val version: String, val apiVersions: List<String>) {
    companion object {
        fun current() = ServerInfoDto("ThermoBox", BuildInfo.texte, listOf("v1"))
    }
}
