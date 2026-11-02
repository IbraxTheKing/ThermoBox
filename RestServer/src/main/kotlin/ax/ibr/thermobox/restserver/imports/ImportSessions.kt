package ax.ibr.thermobox.restserver.imports

import ax.ibr.thermobox.business.users.imports.ImportTable
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * The lists being imported, between their reading and the confirmation: kept in memory only (they
 * hold personal data), for the admin who gave them, half an hour at most, a few per admin.
 */
object ImportSessions {

    private val TTL: Duration = Duration.ofMinutes(30)
    private const val PER_OWNER = 5

    class Session(val id: String, val owner: Long, val source: String, val table: ImportTable, val created: Instant)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    fun open(owner: Long, source: String, table: ImportTable, now: Instant = Instant.now()): Session {
        sweep(now)
        // The oldest of this admin goes when they have too many
        sessions.values.filter { it.owner == owner }.sortedBy { it.created }.dropLast(PER_OWNER - 1).forEach { sessions.remove(it.id) }
        val id = ByteArray(18).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        return Session(id, owner, source, table, now).also { sessions[id] = it }
    }

    /** The session [id] of [owner], null if unknown, expired or someone else's. */
    fun get(id: String, owner: Long, now: Instant = Instant.now()): Session? {
        sweep(now)
        return sessions[id]?.takeIf { it.owner == owner }
    }

    fun close(id: String) {
        sessions.remove(id)
    }

    private fun sweep(now: Instant) {
        sessions.values.removeIf { it.created.plus(TTL).isBefore(now) }
    }
}
