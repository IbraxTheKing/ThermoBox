package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.update.UpdateConfig
import ax.ibr.thermobox.restserver.dto.SettingsDto
import ax.ibr.thermobox.restserver.dto.SettingsRequest
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.nio.file.Files
import java.nio.file.Path as FilePath
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Display settings an ADMIN chooses for every user, whatever the client: today the look of the
 * pages, "glass" (translucent surfaces, colored background) or "panel" (flat control panel).
 *
 * Kept in `interface.properties` of the data folder (THERMOBOX_DATA_DIR).
 */
@Path("api/v2/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SettingsResource {

    @GET
    fun read(): SettingsDto = SettingsDto(style())

    @PUT
    @RequiresRole("ADMIN")
    fun update(request: SettingsRequest?): Response {
        val style = request?.style?.trim()?.lowercase()
        if (style !in STYLES) return Response.status(Response.Status.BAD_REQUEST)
            .entity(mapOf("error" to "style must be one of ${STYLES.joinToString()}")).build()
        return try {
            write("style", style!!)
            Response.ok(SettingsDto(style)).build()
        } catch (e: Exception) {
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "The data folder is not writable: ${e.message}")).build()
        }
    }

    private companion object {
        val STYLES = listOf("glass", "panel")

        /** Values written by the versions before 26.11, read as their English equivalent. */
        private val LEGACY_STYLES = mapOf("verre" to "glass", "panneau" to "panel")

        val file: FilePath by lazy {
            runCatching { UpdateConfig.fromEnvironment().directory }
                .getOrElse { FilePath.of(System.getProperty("user.home"), ".thermobox") }
                .resolve("interface.properties")
        }

        fun read(): Properties = Properties().apply {
            if (Files.exists(file)) runCatching { Files.newBufferedReader(file).use { load(it) } }
        }

        fun style(): String = read().getProperty("style")?.let { LEGACY_STYLES[it] ?: it }?.takeIf { it in STYLES } ?: STYLES.first()

        @Synchronized
        fun write(key: String, value: String) {
            val p = read()
            p.setProperty(key, value)
            Files.createDirectories(file.parent)
            // Written aside then moved: a reader never sees half a file
            val tmp = file.resolveSibling("interface.properties.tmp")
            Files.newBufferedWriter(tmp).use { p.store(it, "ThermoBox: display chosen by an administrator") }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
