package ax.ibr.thermobox.business.security

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.User
import java.security.MessageDigest

/** Credential check shared by the REST login and the JSF login. */
object Authenticator {

    private const val DUMMY_PASSWORD = "\u0000no-such-user\u0000"

    /** Returns the user if the credentials are valid, null otherwise. */
    fun authenticate(username: String, password: String): User? {
        if (username.isBlank() || password.isEmpty()) return null
        val user = BusinessFactory.getUserService().getByUsername(username.trim())
        // Always run the comparison so the response time doesn't reveal whether the username exists.
        val passwordOk = passwordMatches(password, user?.password ?: DUMMY_PASSWORD)
        return if (user != null && passwordOk) user else null
    }

    // Passwords are still stored in clear text. Hashing both sides first makes the comparison
    // constant-time regardless of length.
    // TODO: switch to a password hash (BCrypt/Argon2), here and at registration.
    private fun passwordMatches(raw: String, stored: String): Boolean {
        val sha = MessageDigest.getInstance("SHA-256")
        return MessageDigest.isEqual(sha.digest(raw.toByteArray()), sha.digest(stored.toByteArray()))
    }
}
