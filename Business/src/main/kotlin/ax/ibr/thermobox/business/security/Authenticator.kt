package ax.ibr.thermobox.business.security

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.UserService
import java.security.MessageDigest
import java.util.UUID

/** Credential check shared by the REST login and the JSF login. */
object Authenticator {

    /** Hash of a random value: unknown usernames pay the same BCrypt cost as real ones. */
    private val DUMMY_HASH: String by lazy { PasswordHasher.hash(UUID.randomUUID().toString()) }

    /** Returns the user if the credentials are valid, null otherwise. */
    fun authenticate(username: String, password: String): User? =
        authenticate(username, password, BusinessFactory.getUserService())

    /** Same, with the user service given (tests pass an in-memory one). */
    internal fun authenticate(username: String, password: String, users: UserService): User? {
        if (username.isBlank() || password.isEmpty() || PasswordHasher.isTooLong(password)) return null
        val user = users.getByUsername(username.trim())
        val stored = user?.password

        if (user == null || stored == null) {
            // Same work as a real check, so the response time doesn't reveal whether the username exists.
            PasswordHasher.matches(password, DUMMY_HASH)
            return null
        }
        if (PasswordHasher.isHash(stored)) {
            return if (PasswordHasher.matches(password, stored)) user else null
        }

        // Account from before hashing (clear text in database): accept it once, then store the
        // hash in its place, so existing accounts migrate as their owners log in.
        PasswordHasher.matches(password, DUMMY_HASH)
        if (!legacyMatches(password, stored)) return null
        user.password = password
        users.update(user) // UserService hashes it
        return user
    }

    // Hashing both sides first makes the comparison constant-time regardless of length.
    private fun legacyMatches(raw: String, stored: String): Boolean {
        val sha = MessageDigest.getInstance("SHA-256")
        return MessageDigest.isEqual(sha.digest(raw.toByteArray()), sha.digest(stored.toByteArray()))
    }
}
