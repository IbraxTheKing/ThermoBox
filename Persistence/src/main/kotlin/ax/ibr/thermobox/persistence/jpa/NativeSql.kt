package ax.ibr.thermobox.persistence.jpa

import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Runs a plain SQL statement written with `?` placeholders (the shared statements of
 * [ax.ibr.thermobox.persistence.LatestMeasurements]) through JPA, in the transaction of [em].
 * The placeholders are numbered (`?1`, `?2`…), the form JPA guarantees for native queries.
 */
internal fun EntityManager.executeSql(sql: String, vararg params: Any?): Int {
    var n = 0
    val numbered = sql.replace(Regex("""\?""")) { "?${++n}" }
    val query = createNativeQuery(numbered)
    params.forEachIndexed { i, p -> query.setParameter(i + 1, if (p is LocalDateTime) Timestamp.valueOf(p) else p) }
    return query.executeUpdate()
}
