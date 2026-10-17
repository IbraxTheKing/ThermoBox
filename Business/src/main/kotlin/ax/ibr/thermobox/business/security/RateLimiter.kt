package ax.ibr.thermobox.business.security

import java.util.concurrent.ConcurrentHashMap
import java.time.Instant

object RateLimiter {
    private val attempts = ConcurrentHashMap<String, LoginAttempt>()
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_SECONDS = 60L

    data class LoginAttempt(val count: Int, val lastAttempt: Instant)

    fun isBlocked(key: String): Boolean {
        val attempt = attempts[key] ?: return false
        if (Instant.now().isBefore(attempt.lastAttempt.plusSeconds(LOCKOUT_DURATION_SECONDS))) {
            return attempt.count >= MAX_ATTEMPTS
        }
        return false
    }

    fun recordFailure(key: String) {
        attempts.compute(key) { _, old ->
            val count = (old?.count ?: 0) + 1
            LoginAttempt(count, Instant.now())
        }
    }

    fun reset(key: String) {
        attempts.remove(key)
    }
}
