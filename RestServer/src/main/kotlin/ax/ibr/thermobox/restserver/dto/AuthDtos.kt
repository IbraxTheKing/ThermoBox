package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType

/** Public view of a [User]: the API never sends the password back. */
data class UserDto(val id: Long?, val username: String?, val type: UserType?) {
    companion object {
        fun from(user: User) = UserDto(user.id, user.username, user.type)
    }
}

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
