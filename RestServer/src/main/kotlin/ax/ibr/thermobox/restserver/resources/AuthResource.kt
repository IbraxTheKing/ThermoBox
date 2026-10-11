package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.security.Authenticator
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.restserver.dto.LoginRequest
import ax.ibr.thermobox.restserver.dto.LoginResponse
import ax.ibr.thermobox.restserver.dto.UserDto
import ax.ibr.thermobox.restserver.security.JwtSecurityContext
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.utils.rest.PublicEndpoint
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

@Path("api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuthResource {

    /** Exchanges credentials for a JWT, to send back as `Authorization: Bearer <token>`. */
    @POST
    @Path("/login")
    @PublicEndpoint
    fun login(credentials: LoginRequest?): Response {
        val username = credentials?.username?.trim().orEmpty()
        val password = credentials?.password.orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "username and password are required")
        }

        val user = Authenticator.authenticate(username, password)
            ?: return error(Response.Status.UNAUTHORIZED, "Invalid username or password")

        val issued = JwtService.issue(user)
        return Response.ok(
            LoginResponse(issued.token, "Bearer", issued.expiresAt.toString(), UserDto.from(user))
        ).build()
    }

    /** Revokes the token used for this request. */
    @POST
    @Path("/logout")
    fun logout(@Context securityContext: SecurityContext): Response {
        JwtService.revoke((securityContext as JwtSecurityContext).token)
        return Response.noContent().build()
    }

    @GET
    @Path("/me")
    fun me(@Context securityContext: SecurityContext): UserDto =
        UserDto.from(securityContext.currentUser)

    private fun error(status: Response.Status, message: String): Response =
        Response.status(status).entity(mapOf("error" to message)).build()
}
