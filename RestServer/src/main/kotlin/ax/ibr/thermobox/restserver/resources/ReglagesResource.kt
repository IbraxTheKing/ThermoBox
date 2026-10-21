package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.update.ConfigMisesAJour
import ax.ibr.thermobox.restserver.dto.ReglagesDto
import ax.ibr.thermobox.restserver.dto.ReglagesRequest
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
 * pages, "verre" (translucent surfaces, coloured background) or "panneau" (flat control panel).
 *
 * Kept in `interface.properties` of the data folder (THERMOBOX_DATA_DIR), the file the JSF
 * interface uses too: when both share the folder, the choice applies to both.
 */
@Path("api/reglages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ReglagesResource {

    @GET
    fun lire(): ReglagesDto = ReglagesDto(style())

    @PUT
    @RequiresRole("ADMIN")
    fun modifier(demande: ReglagesRequest?): Response {
        val style = demande?.style?.trim()?.lowercase()
        if (style !in STYLES) return Response.status(Response.Status.BAD_REQUEST)
            .entity(mapOf("error" to "style must be one of ${STYLES.joinToString()}")).build()
        return try {
            ecrire("style", style!!)
            Response.ok(ReglagesDto(style)).build()
        } catch (e: Exception) {
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Le dossier de données n'est pas accessible en écriture : ${e.message}")).build()
        }
    }

    private companion object {
        val STYLES = listOf("verre", "panneau")

        val fichier: FilePath by lazy {
            runCatching { ConfigMisesAJour.depuisEnvironnement().dossier }
                .getOrElse { FilePath.of(System.getProperty("user.home"), ".thermobox") }
                .resolve("interface.properties")
        }

        fun lire(): Properties = Properties().apply {
            if (Files.exists(fichier)) runCatching { Files.newBufferedReader(fichier).use { load(it) } }
        }

        fun style(): String = lire().getProperty("style")?.takeIf { it in STYLES } ?: STYLES.first()

        @Synchronized
        fun ecrire(cle: String, valeur: String) {
            val p = lire()
            p.setProperty(cle, valeur)
            Files.createDirectories(fichier.parent)
            // Written aside then moved: a reader never sees half a file
            val tmp = fichier.resolveSibling("interface.properties.tmp")
            Files.newBufferedWriter(tmp).use { p.store(it, "ThermoBox : affichage choisi par un administrateur") }
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
