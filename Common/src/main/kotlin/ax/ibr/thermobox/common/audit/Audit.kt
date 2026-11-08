package ax.ibr.thermobox.common.audit

import java.time.LocalDateTime

/**
 * One change made through the API (ThermoBox 26.12): who ([username], [role] at the time), when,
 * what ([action], « setpoint.set », « rule.create »…) on what ([targetType], [targetId], and its
 * [targetName] at the time, kept after it is deleted). [details]: a JSON object, the values sent
 * (secrets removed) and, for some actions, those before ("before").
 */
data class AuditEntry(
    val id: Long? = null,
    val at: LocalDateTime,
    val userId: Long?,
    val username: String,
    val role: String?,
    val action: String,
    val targetType: String? = null,
    val targetId: Long? = null,
    val targetName: String? = null,
    val details: String? = null,
)

/**
 * Which entries to read: by [userId], an [action] or the actions of a domain (« rule » for
 * rule.create, rule.update…), a target, a period, a [text] in the user, target or details; the
 * most recent first, [offset] and [limit] for the pages.
 */
data class AuditQuery(
    val userId: Long? = null,
    val action: String? = null,
    val targetType: String? = null,
    val targetId: Long? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val text: String? = null,
    val offset: Int = 0,
    val limit: Int = 50,
)
