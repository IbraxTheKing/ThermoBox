package ax.ibr.thermobox.business.security

import ax.ibr.thermobox.common.entities.User
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime

/**
 * Provisional passwords: given to an imported account, to the first admin, or by an admin who
 * resets a password. Drawn from [SecureRandom], shown once to the one who asked for them, then
 * only their BCrypt hash is kept ([PasswordHasher], through the UserService); the account must
 * change it at its first login ([User.passwordChangeRequired]) and it expires.
 *
 * `THERMOBOX_PROVISIONAL_PASSWORD_DAYS`: validity, 7 days by default (1 to 90).
 */
object ProvisionalPasswords {

    /** No 0 / O, 1 / l / I: read aloud or copied from paper without mistakes. */
    private const val LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val ALPHABET = LETTERS + DIGITS

    /** 3 groups of 4: 12 signs out of 56, about 69 bits. */
    private const val GROUPS = 3
    private const val GROUP = 4

    private val random = SecureRandom()

    val defaultValidity: Duration = Duration.ofDays(
        System.getenv("THERMOBOX_PROVISIONAL_PASSWORD_DAYS")?.toLongOrNull()?.coerceIn(1, 90) ?: 7
    )

    /** A new password such as `k7Pm-Xq3t-R9wz`: letters of both cases and figures. */
    fun generate(): String {
        while (true) {
            val signs = CharArray(GROUPS * GROUP) { ALPHABET[random.nextInt(ALPHABET.length)] }
            val text = signs.concatToString()
            // Letters of both cases and a figure, as the policy asks of the passwords chosen
            if (text.any { it.isDigit() } && text.any { it.isUpperCase() } && text.any { it.isLowerCase() }) {
                return text.chunked(GROUP).joinToString("-")
            }
        }
    }

    /**
     * Gives [user] a new provisional password (hashed when the UserService saves it) valid for
     * [validity] (null: no limit), and returns it in clear: the only time it exists.
     */
    fun assign(user: User, validity: Duration? = defaultValidity, now: LocalDateTime = LocalDateTime.now()): String {
        val password = generate()
        user.password = password
        user.passwordChangeRequired = true
        user.provisionalExpiresAt = validity?.let { now.plus(it) }
        return password
    }

    /** The provisional password of [user] is out of date. */
    fun expired(user: User, now: LocalDateTime = LocalDateTime.now()): Boolean =
        user.passwordChangeRequired && user.provisionalExpiresAt?.isBefore(now) == true
}
