package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import java.time.LocalDateTime

/** The log of the changes made through the API (table audit_log, migration V10). */
interface AuditDataService {
    /** Adds [entry]; answers it with its id. */
    fun add(entry: AuditEntry): AuditEntry

    /** The entries [query] asks for, the most recent first. */
    fun find(query: AuditQuery): List<AuditEntry>

    /** How many entries [query] matches (its offset and limit aside). */
    fun count(query: AuditQuery): Long

    /** The users who appear in the log, with their latest name (user id, username), by name. */
    fun users(): List<Pair<Long?, String>>

    /** Removes the entries before [before]; answers how many. */
    fun purge(before: LocalDateTime): Int
}
