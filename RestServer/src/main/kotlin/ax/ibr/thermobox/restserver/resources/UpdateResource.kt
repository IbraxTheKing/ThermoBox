package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.update.OptionsInstallation
import ax.ibr.thermobox.restserver.dto.ErrorResponse
import ax.ibr.thermobox.restserver.dto.InstallRequest
import ax.ibr.thermobox.restserver.dto.InstallationDto
import ax.ibr.thermobox.restserver.dto.PreparationDto
import ax.ibr.thermobox.restserver.dto.UpdateStatusDto
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

/**
 * Updates of this REST server. Reading is open to every authenticated user (a third-party
 * application can tell its users that a version is out); checking and installing are ADMIN only.
 */
@Path("api/updates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UpdateResource {

    private val service = BusinessFactory.getUpdateService()

    @Context
    private lateinit var security: SecurityContext

    private val admin: Boolean get() = security.isUserInRole("ADMIN")

    /** Installed version, available versions (patch / feature), last check. */
    @GET
    @RequiresAuth
    fun etat(): UpdateStatusDto = UpdateStatusDto.from(service.getEtat(), admin)

    /** Checks GitHub now (also done once a day). */
    @POST
    @Path("check")
    @RequiresRole("ADMIN")
    fun verifier(): UpdateStatusDto = UpdateStatusDto.from(service.verifier(), admin = true)

    /** Progress of the last installation; 204 if there has been none. */
    @GET
    @Path("installation")
    @RequiresRole("ADMIN")
    fun installation(): Response =
        service.getEtat().installation?.let { Response.ok(InstallationDto.from(it)).build() } ?: Response.noContent().build()

    /** Checks, risks and backup policy before installing [version]. */
    @GET
    @Path("{version}")
    @RequiresRole("ADMIN")
    fun preparer(@PathParam("version") version: String): Response = try {
        Response.ok(PreparationDto.from(service.preparer(version))).build()
    } catch (e: IllegalArgumentException) {
        erreur(Response.Status.NOT_FOUND, e)
    }

    /**
     * Starts the installation (202): progress at GET /api/updates/installation. The server then
     * restarts on the new version. A feature update needs "acceptRisks": true.
     */
    @POST
    @Path("{version}/install")
    @RequiresRole("ADMIN")
    fun installer(@PathParam("version") version: String, demande: InstallRequest?): Response {
        val options = OptionsInstallation(sauvegarderBase = demande?.backupDatabase ?: true, risquesAcceptes = demande?.acceptRisks ?: false)
        return try {
            val installation = service.installer(version, security.currentUser.username.orEmpty(), options)
            Response.accepted(InstallationDto.from(installation)).build()
        } catch (e: IllegalArgumentException) {
            erreur(Response.Status.BAD_REQUEST, e)
        } catch (e: IllegalStateException) {
            erreur(Response.Status.CONFLICT, e)
        }
    }

    private fun erreur(status: Response.Status, e: Exception): Response =
        Response.status(status).entity(ErrorResponse(status.statusCode, e.message ?: status.reasonPhrase)).build()
}
