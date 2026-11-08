package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import ax.ibr.thermobox.persistence.dataservices.AuditDataService
import java.sql.ResultSet
import java.time.LocalDateTime

/** [AuditDataService] in plain SQL (table audit_log of migration V10). */
class AuditDataServiceJdbcImpl(private val db: JdbcDatabase) : AuditDataService {

    override fun add(entry: AuditEntry): AuditEntry = db.transaction { cx ->
        val id = cx.insert(
            "INSERT INTO audit_log (at, user_id, username, role, action, target_type, target_id, target_name, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            entry.at, entry.userId, entry.username.take(100), entry.role?.take(20), entry.action.take(60), entry.targetType?.take(30),
            entry.targetId, entry.targetName?.take(255), entry.details,
        )
        entry.copy(id = id)
    }

    override fun find(query: AuditQuery): List<AuditEntry> = db.read { cx ->
        val (where, params) = where(query)
        cx.query(
            "SELECT * FROM audit_log $where ORDER BY id DESC LIMIT ? OFFSET ?",
            *(params + query.limit.coerceIn(1, 500) + query.offset.coerceAtLeast(0)).toTypedArray(),
        ) { entryOf(it) }
    }

    override fun count(query: AuditQuery): Long = db.read { cx ->
        val (where, params) = where(query)
        cx.query("SELECT COUNT(*) AS n FROM audit_log $where", *params.toTypedArray()) { it.getLong("n") }.firstOrNull() ?: 0L
    }

    override fun users(): List<Pair<Long?, String>> = db.read { cx ->
        // The latest name of each user (one renamed shows once)
        cx.query(
            "SELECT a.user_id, a.username FROM audit_log a JOIN (SELECT user_id, MAX(id) AS last FROM audit_log WHERE user_id IS NOT NULL GROUP BY user_id) l " +
                "ON a.id = l.last ORDER BY a.username",
        ) { it.longOrNull("user_id") to it.getString("username") }
    }

    override fun purge(before: LocalDateTime): Int = db.transaction { cx -> cx.execute("DELETE FROM audit_log WHERE at < ?", before) }

    private fun where(q: AuditQuery): Pair<String, List<Any?>> {
        val clauses = ArrayList<String>()
        val params = ArrayList<Any?>()
        q.userId?.let { clauses += "user_id = ?"; params += it }
        q.action?.takeIf { it.isNotBlank() }?.let {
            // « rule »: every action on rules; « rule.create »: that one
            if ('.' in it) { clauses += "action = ?"; params += it } else { clauses += "action LIKE ?"; params += "${like(it)}.%" }
        }
        q.targetType?.let { clauses += "target_type = ?"; params += it }
        q.targetId?.let { clauses += "target_id = ?"; params += it }
        q.from?.let { clauses += "at >= ?"; params += it }
        q.to?.let { clauses += "at < ?"; params += it }
        q.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val p = "%${like(it)}%"
            clauses += "(username LIKE ? OR target_name LIKE ? OR details LIKE ?)"
            params.addAll(listOf(p, p, p))
        }
        return (if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")) to params
    }

    /** [s] for LIKE: its % and _ taken as they are. */
    private fun like(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun entryOf(rs: ResultSet) = AuditEntry(
        id = rs.getLong("id"),
        at = rs.dateTime("at")!!,
        userId = rs.longOrNull("user_id"),
        username = rs.getString("username"),
        role = rs.getString("role"),
        action = rs.getString("action"),
        targetType = rs.getString("target_type"),
        targetId = rs.longOrNull("target_id"),
        targetName = rs.getString("target_name"),
        details = rs.getString("details"),
    )
}
