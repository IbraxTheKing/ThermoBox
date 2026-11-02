package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType

/**
 * Public view of a [User]: the API never sends the password back. [mustChangePassword]: the
 * password is a provisional one, to be replaced before anything else (until
 * [provisionalExpiresAt], ISO local date-time, null: no limit).
 */
data class UserDto(
    val id: Long?,
    val username: String?,
    val type: UserType?,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val mustChangePassword: Boolean = false,
    val provisionalExpiresAt: String? = null,
) {
    companion object {
        fun from(user: User) = UserDto(
            user.id, user.username, user.type, user.firstName, user.lastName, user.email,
            user.passwordChangeRequired, user.provisionalExpiresAt?.takeIf { user.passwordChangeRequired }?.toString(),
        )
    }
}

/** POST /api/v2/auth/password. */
class ChangePasswordRequest {
    var currentPassword: String? = null
    var newPassword: String? = null
}

/** A provisional password given by an admin: shown once, never stored in clear. */
data class ProvisionalPasswordDto(val id: Long?, val username: String?, val password: String, val expiresAt: String?)

class LoginRequest {
    var username: String? = null
    var password: String? = null
}

data class LoginResponse(
    val token: String,
    val tokenType: String,
    val expiresAt: String,
    val user: UserDto
)
