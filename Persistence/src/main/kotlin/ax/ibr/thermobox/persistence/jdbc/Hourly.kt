package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.persistence.HourlyMeasurements
import java.sql.Connection
import java.time.LocalDateTime

/** Upkeep of `measurement_hourly` in a JDBC transaction (see [HourlyMeasurements]). */
internal object Hourly {

    /** A new measurement [value] of [roomId] at [date]: counted in its hour. */
    fun add(cx: Connection, roomId: Long?, date: LocalDateTime?, value: Float?) {
        if (roomId == null || date == null || value == null) return
        cx.execute(HourlyMeasurements.ADD, roomId, HourlyMeasurements.hourOf(date), value, value, value)
    }

    /** The device and date of measurement [temperatureId] as stored, null if it is not a linked measurement. */
    fun owner(cx: Connection, temperatureId: Long?): Pair<Long, LocalDateTime>? {
        temperatureId ?: return null
        return cx.query(HourlyMeasurements.OWNER, temperatureId) { rs ->
            val room = rs.longOrNull("room_id")
            val date = rs.dateTime("date")
            if (room != null && date != null) room to date else null
        }.firstOrNull()
    }

    /** The hour of [date] of [roomId] worked out again from the history. */
    fun rebuild(cx: Connection, roomId: Long, date: LocalDateTime) {
        val hour = HourlyMeasurements.hourOf(date)
        cx.execute(HourlyMeasurements.FORGET, roomId, hour)
        cx.execute(HourlyMeasurements.REBUILD, roomId, hour, hour.plusHours(1))
    }

    fun rebuild(cx: Connection, owners: Collection<Pair<Long, LocalDateTime>?>) {
        owners.filterNotNull().distinctBy { (r, d) -> r to HourlyMeasurements.hourOf(d) }.forEach { (r, d) -> rebuild(cx, r, d) }
    }
}
