package ax.ibr.thermobox.restserver.security

import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Issues and validates the HS256 JWTs used by the REST API.
 *
 * Configuration (environment variables):
 * - `THERMOBOX_JWT_SECRET`: signing key, at least 32 bytes. If unset, a random key is
 *   generated at startup, so every token becomes invalid when the server restarts.
 * - `THERMOBOX_JWT_TTL_MINUTES`: token lifetime, 60 minutes by default.
 */
object JwtService {

    private const val ISSUER = "thermobox-rest"
    private const val MIN_SECRET_BYTES = 32

    val tokenTtl: Duration = Duration.ofMinutes(
        System.getenv("THERMOBOX_JWT_TTL_MINUTES")?.toLongOrNull()?.takeIf { it > 0 } ?: 60
    )

    private val algorithm = Algorithm.HMAC256(loadSecret())
    private val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    // jti -> expiry of logged-out tokens. Kept in memory: a restart clears it, but tokens
    // signed with a random key don't survive a restart either, and the TTL bounds the risk.
    private val revoked = ConcurrentHashMap<String, Instant>()

    fun issue(user: User): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(tokenTtl)
        val token = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(user.id.toString())
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
        return IssuedToken(token, expiresAt)
    }

    /**
     * Returns the decoded token if its signature, issuer and expiry are valid and it has not
     * been revoked, `null` otherwise.
     */
    fun verify(token: String): DecodedJWT? {
        val jwt = try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            return null
        }
        // We always set these claims; a token without them was not issued by this server.
        if (jwt.id == null || jwt.expiresAt == null || jwt.subject == null) return null
        if (revoked.containsKey(jwt.id)) return null
        return jwt
    }

    fun revoke(jwt: DecodedJWT) {
        val now = Instant.now()
        revoked.entries.removeIf { it.value.isBefore(now) }
        revoked[jwt.id] = jwt.expiresAt.toInstant()
    }

    private fun loadSecret(): ByteArray {
        val configured = System.getenv("THERMOBOX_JWT_SECRET")
        if (configured != null) {
            val bytes = configured.toByteArray(Charsets.UTF_8)
            require(bytes.size >= MIN_SECRET_BYTES) {
                "THERMOBOX_JWT_SECRET must be at least $MIN_SECRET_BYTES bytes long"
            }
            return bytes
        }
        System.err.println("THERMOBOX_JWT_SECRET is not set: using a random key, tokens will not survive a restart.")
        return ByteArray(64).also { SecureRandom().nextBytes(it) }
    }
}

data class IssuedToken(val token: String, val expiresAt: Instant)
