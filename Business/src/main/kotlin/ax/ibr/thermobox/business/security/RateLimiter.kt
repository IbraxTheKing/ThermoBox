package ax.ibr.thermobox.business.security

import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Failed logins per account: past [MAX_ATTEMPTS], the next ones are refused for
 * [LOCKOUT_DURATION_SECONDS] (POST /api/v2/auth/login and /api/v2/auth/password).
 *
 * The key is the username as the database compares it: MySQL finds « admin » for « ADMIN »,
 * « Admin » or « ädmin » (case- and accent-insensitive collation), so each spelling had its own
 * count before: five more tries for every one of them. Entries past their lockout are dropped
 * now and then, so random usernames don't fill the memory.
 */
object RateLimiter {
    private val attempts = ConcurrentHashMap<String, LoginAttempt>()
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_SECONDS = 60L
    /** Past this many entries, the expired ones are dropped before adding another. */
    private const val PURGE_ABOVE = 10_000

    data class LoginAttempt(val count: Int, val lastAttempt: Instant)

    /** « Ädmin » → « admin »: the username the way MySQL matches it. */
    internal fun key(username: String): String =
        Normalizer.normalize(username.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun isBlocked(username: String): Boolean {
        val attempt = attempts[key(username)] ?: return false
        if (Instant.now().isBefore(attempt.lastAttempt.plusSeconds(LOCKOUT_DURATION_SECONDS))) {
            return attempt.count >= MAX_ATTEMPTS
        }
        return false
    }

    fun recordFailure(username: String) {
        if (attempts.size > PURGE_ABOVE) purge()
        attempts.compute(key(username)) { _, old ->
            val count = (old?.count ?: 0) + 1
            LoginAttempt(count, Instant.now())
        }
    }

    fun reset(username: String) {
        attempts.remove(key(username))
    }

    private fun purge() {
        val limit = Instant.now().minusSeconds(LOCKOUT_DURATION_SECONDS)
        attempts.entries.removeIf { it.value.lastAttempt.isBefore(limit) }
    }
}
