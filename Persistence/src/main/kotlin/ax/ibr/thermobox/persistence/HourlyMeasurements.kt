package ax.ibr.thermobox.persistence

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Table `measurement_hourly` (migration V7): for each device and each hour, the number, sum,
 * minimum and maximum of its measurements. A curve over days or weeks is drawn from these hourly
 * averages instead of every measurement.
 *
 * Both implementations keep it up to date with these statements:
 * - a measurement linked to a device: [ADD] counts it in its hour;
 * - a measurement changed, moved or deleted: the hours concerned are worked out again from the
 *   history ([FORGET] then [REBUILD], a range of the index (room_id, kind, date)).
 */
object HourlyMeasurements {

    /** Parameters: room id, hour, value, value, value. */
    const val ADD =
        "INSERT INTO measurement_hourly (room_id, hour, count, total, min_value, max_value) VALUES (?, ?, 1, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE count = count + 1, total = total + VALUES(total), " +
            "min_value = LEAST(min_value, VALUES(min_value)), max_value = GREATEST(max_value, VALUES(max_value))"

    /** Parameters: room id, hour. */
    const val FORGET = "DELETE FROM measurement_hourly WHERE room_id = ? AND hour = ?"

    /** Parameter: room id. */
    const val FORGET_ROOM = "DELETE FROM measurement_hourly WHERE room_id = ?"

    /** Parameters: room id, start of the hour, end of the hour (excluded). After [FORGET]. */
    const val REBUILD =
        "INSERT INTO measurement_hourly (room_id, hour, count, total, min_value, max_value) " +
            "SELECT e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00'), COUNT(*), SUM(t.value), MIN(t.value), MAX(t.value) " +
            "FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.kind = 'Measurement' AND e.date >= ? AND e.date < ? AND t.value IS NOT NULL " +
            "GROUP BY e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00')"

    /** Parameter: room id. Every hour of a device, from its history (a whole device at once). */
    const val REBUILD_ROOM =
        "INSERT INTO measurement_hourly (room_id, hour, count, total, min_value, max_value) " +
            "SELECT e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00'), COUNT(*), SUM(t.value), MIN(t.value), MAX(t.value) " +
            "FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.kind = 'Measurement' AND e.date IS NOT NULL AND t.value IS NOT NULL " +
            "GROUP BY e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00')"

    /** Parameter: temperature id. The device and the date of a measurement, before it changes. */
    const val OWNER = "SELECT room_id, date FROM room_temperature_attr WHERE temperature_id = ? AND kind = 'Measurement'"

    /** Parameters: start hour, end, then the room ids. Columns: room id, hour, average. */
    fun averages(rooms: Int) =
        "SELECT room_id, hour, total / count AS average FROM measurement_hourly " +
            "WHERE hour >= ? AND hour <= ? AND room_id IN (${TemperatureHistory.inList(rooms)}) ORDER BY room_id, hour"

    fun hourOf(date: LocalDateTime): LocalDateTime = date.truncatedTo(ChronoUnit.HOURS)
}
