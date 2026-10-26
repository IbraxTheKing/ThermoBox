package ax.ibr.thermobox.business.security

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * BCrypt hashing of user passwords: only hashes are stored. Each hash carries its own random
 * salt and cost, so a leaked database can't be reversed with precomputed tables and is slow to
 * brute-force.
 */
object PasswordHasher {

    /** 2^12 rounds: about a quarter of a second per hash, paid only at login and on password change. */
    private const val COST = 12

    /** BCrypt only uses the first 72 bytes: longer passwords are refused rather than silently cut. */
    const val MAX_BYTES = 72

    // Modular crypt format: $2a$ / $2b$ / $2y$, two-digit cost, 22 chars of salt + 31 of hash.
    private val BCRYPT_HASH = Regex("""^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$""")

    fun isTooLong(password: String): Boolean = password.toByteArray(Charsets.UTF_8).size > MAX_BYTES

    /** @throws IllegalArgumentException if [password] is longer than [MAX_BYTES] bytes. */
    fun hash(password: String): String {
        require(!isTooLong(password)) { "The password must not exceed $MAX_BYTES octets." }
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray())
    }

    fun isHash(value: String?): Boolean = value != null && BCRYPT_HASH.matches(value)

    /** Checks [password] against a stored hash; false for a malformed hash or a too long password. */
    fun matches(password: String, hash: String): Boolean =
        !isTooLong(password) && BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
