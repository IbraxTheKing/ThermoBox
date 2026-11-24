package ax.ibr.thermobox.restserver.resources

import ax.ibr.utils.rest.PublicEndpoint
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.Properties

/**
 * The ThermoBox server itself, for a client to know what it talks to before anyone logs in:
 * `GET /api/server` → `{"name": "ThermoBox", "version": "26.10.5", "apiVersions": ["v1"]}`.
 *
 * Same answer, same keys as 26.11; from 26.12 on [ServerInfoDto.apiVersions] also lists "v2".
 */
@Path("api/server")
@PublicEndpoint
class ServerResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun info(): ServerInfoDto = ServerInfoDto.current()
}

/**
 * [version]: tag of the release ("26.10.5"), or "26.10" for a build without -Dthermobox.version.
 * [apiVersions]: the REST APIs this server answers, oldest first.
 */
data class ServerInfoDto(val name: String, val version: String, val apiVersions: List<String>) {
    companion object {
        /** thermobox-version.properties, filled in by Maven (resource filtering). */
        private val buildVersion: String by lazy {
            val props = Properties()
            ServerResource::class.java.getResourceAsStream("/thermobox-version.properties")?.use { props.load(it) }
            props.getProperty("version")?.trim()?.takeUnless { it.isEmpty() || it.startsWith("\${") } ?: "26.10"
        }

        fun current() = ServerInfoDto("ThermoBox", buildVersion, listOf("v1"))
    }
}
