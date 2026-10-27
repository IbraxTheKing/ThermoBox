package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
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

/** Rows of a plain SQL query written with `?` placeholders, each row an array of its columns. */
internal fun EntityManager.querySql(sql: String, vararg params: Any?): List<Array<Any?>> {
    var n = 0
    val numbered = sql.replace(Regex("""\?""")) { "?${++n}" }
    val query = createNativeQuery(numbered)
    params.forEachIndexed { i, p -> query.setParameter(i + 1, if (p is LocalDateTime) Timestamp.valueOf(p) else p) }
    @Suppress("UNCHECKED_CAST")
    return (query.resultList as List<Any?>).map { if (it is Array<*>) it as Array<Any?> else arrayOf(it) }
}

/**
 * A plain (unmanaged) temperature from columns id, DTYPE, value, date starting at [at]
 * ([ax.ibr.thermobox.persistence.TemperatureHistory.COLUMNS]).
 */
internal fun temperatureOf(row: Array<Any?>, at: Int = 0): Temperature {
    val value = (row[at + 2] as Number?)?.toFloat()
    val date = when (val d = row[at + 3]) {
        is Timestamp -> d.toLocalDateTime()
        is LocalDateTime -> d
        else -> null
    }
    val t = when (row[at + 1] as String?) {
        "Setpoint" -> Setpoint(value ?: 0f, date ?: LocalDateTime.MIN)
        "Measurement" -> Measurement(value ?: 0f, date ?: LocalDateTime.MIN)
        else -> Temperature()
    }
    t.value = value
    t.date = date
    t.id = (row[at] as Number).toLong()
    return t
}

/** DTYPE of a temperature (same values as the JPA discriminators). */
internal fun kindOf(t: Temperature): String = when (t) {
    is Setpoint -> "Setpoint"
    is Measurement -> "Measurement"
    else -> "Temperature"
}

internal fun kindOf(type: Class<out Temperature>): String = when (type) {
    Setpoint::class.java -> "Setpoint"
    Measurement::class.java -> "Measurement"
    else -> "Temperature"
}
