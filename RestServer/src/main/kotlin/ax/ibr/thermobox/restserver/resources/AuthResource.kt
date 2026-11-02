package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.security.Authenticator
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.business.security.PasswordPolicy
import ax.ibr.thermobox.business.security.RateLimiter
import ax.ibr.thermobox.business.users.PasswordChange
import ax.ibr.thermobox.restserver.dto.ChangePasswordRequest
import ax.ibr.thermobox.restserver.dto.LoginRequest
import ax.ibr.thermobox.restserver.dto.LoginResponse
import ax.ibr.thermobox.restserver.dto.UserDto
import ax.ibr.thermobox.restserver.security.AllowedWithProvisionalPassword
import ax.ibr.thermobox.restserver.security.currentToken
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.utils.rest.PublicEndpoint
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext

@Path("api/v2/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuthResource {

    /**
     * Exchanges credentials for a JWT, to send back as `Authorization: Bearer <token>`. With a
     * provisional password, `user.mustChangePassword` is true: the client asks for a new one
     * (POST /password) before anything else. An expired provisional password gives 401
     * `"code": "provisional_expired"`.
     */
    @POST
    @Path("/login")
    @PublicEndpoint
    fun login(credentials: LoginRequest?): Response {
        val username = credentials?.username?.trim().orEmpty()
        val password = credentials?.password.orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "username and password are required")
        }

        // Rate Limiting check
        if (RateLimiter.isBlocked(username)) {
            return error(Response.Status.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.")
        }

        val user = when (val result = Authenticator.check(username, password)) {
            is Authenticator.Result.Accepted -> result.user
            Authenticator.Result.ProvisionalExpired -> {
                RateLimiter.reset(username)
                return error(Response.Status.UNAUTHORIZED, "Provisional password expired: ask an administrator for a new one", "provisional_expired")
            }
            Authenticator.Result.Refused -> {
                RateLimiter.recordFailure(username)
                return error(Response.Status.UNAUTHORIZED, "Invalid username or password")
            }
        }

        RateLimiter.reset(username)
        val issued = JwtService.issue(user)
        return Response.ok(
            LoginResponse(issued.token, "Bearer", issued.expiresAt.toString(), UserDto.from(user))
        ).build()
    }

    /** Revokes the token used for this request. */
    @POST
    @Path("/logout")
    @AllowedWithProvisionalPassword
    fun logout(@Context securityContext: SecurityContext): Response {
        JwtService.revoke(securityContext.currentToken)
        return Response.noContent().build()
    }

    @GET
    @Path("/me")
    @AllowedWithProvisionalPassword
    fun me(@Context securityContext: SecurityContext): UserDto =
        UserDto.from(securityContext.currentUser)

    /**
     * The user replaces their password: the current one (the provisional one for an imported
     * account) and the new one, following the rules of GET /password-policy. 204 when done (the
     * account is no longer provisional: the same token reaches everything again); 400 with
     * `"problems": [codes]` when the new one is refused; 403 `wrong_password` when the current
     * one is wrong (counted as a failed login).
     */
    @POST
    @Path("/password")
    @AllowedWithProvisionalPassword
    fun changePassword(request: ChangePasswordRequest?, @Context securityContext: SecurityContext): Response {
        val user = securityContext.currentUser
        val username = user.username.orEmpty()
        val current = request?.currentPassword.orEmpty()
        val new = request?.newPassword.orEmpty()
        if (current.isEmpty() || new.isEmpty()) return error(Response.Status.BAD_REQUEST, "currentPassword and newPassword are required")
        if (RateLimiter.isBlocked(username)) return error(Response.Status.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.")
        return when (val result = PasswordChange.change(user, current, new)) {
            PasswordChange.Result.Done -> {
                RateLimiter.reset(username)
                Response.noContent().build()
            }
            PasswordChange.Result.WrongCurrent -> {
                RateLimiter.recordFailure(username)
                error(Response.Status.FORBIDDEN, "The current password is wrong", "wrong_password")
            }
            is PasswordChange.Result.Refused ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "The new password is refused", "code" to "password_refused", "problems" to result.problems))
                    .build()
        }
    }

    /** The rules of the passwords chosen by their owners, for the forms of the clients. */
    @GET
    @Path("/password-policy")
    @PublicEndpoint
    fun passwordPolicy(): Map<String, Any> = PasswordPolicy.describe()

    private fun error(status: Response.Status, message: String, code: String? = null): Response =
        Response.status(status).entity(if (code == null) mapOf("error" to message) else mapOf("error" to message, "code" to code)).build()
}
