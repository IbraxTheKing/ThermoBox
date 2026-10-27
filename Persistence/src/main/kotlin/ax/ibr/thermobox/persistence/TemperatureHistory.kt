package ax.ibr.thermobox.persistence

/**
 * Reads of the history of an element (migration V6): `room_temperature_attr` keeps a copy of the
 * kind (DTYPE) and of the date of each temperature, so "this device, this kind, this period" is a
 * range of the index (room_id, kind, date) instead of the whole history of the device.
 *
 * Shared by the JPA and JDBC implementations, which keep the copies up to date with [COPY_BY_LINK]
 * and [COPY_BY_TEMPERATURE] on every write. The reads return plain values (id, kind, value, date):
 * no entity is built nor cached for each of the thousands of points of a curve.
 */
object TemperatureHistory {

    /** Selected columns, in this order, by every read below. */
    const val COLUMNS = "t.id AS t_id, t.DTYPE AS t_dtype, t.value AS t_value, t.date AS t_date"

    /** Parameters: kind, date, link id. */
    const val COPY_BY_LINK = "UPDATE room_temperature_attr SET kind = ?, date = ? WHERE id = ?"

    /** Parameters: kind, date, temperature id. After a temperature changed. */
    const val COPY_BY_TEMPERATURE = "UPDATE room_temperature_attr SET kind = ?, date = ? WHERE temperature_id = ?"

    /** Parameters: room id, kind, start, end. Oldest first. */
    const val PERIOD =
        "SELECT $COLUMNS FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.kind = ? AND e.date BETWEEN ? AND ? ORDER BY e.date, e.temperature_id"

    /** Parameters: kind, start, end, then the room ids of the IN list ([inList]). By room, oldest first. */
    fun periodOfRooms(rooms: Int) =
        "SELECT e.room_id AS r, $COLUMNS FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.kind = ? AND e.date BETWEEN ? AND ? AND e.room_id IN (${inList(rooms)}) " +
            "ORDER BY e.room_id, e.date, e.temperature_id"

    /** Parameters: room id, kind. The latest one. */
    const val LATEST =
        "SELECT $COLUMNS FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.kind = ? ORDER BY e.date DESC, e.temperature_id DESC LIMIT 1"

    /** Parameters: kind, then the room ids. Every temperature of that kind of these elements. */
    fun ofRooms(rooms: Int) =
        "SELECT e.room_id AS r, $COLUMNS FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.kind = ? AND e.room_id IN (${inList(rooms)})"

    /** Parameters: room id, start, end. Average of every temperature per day. */
    const val DAILY_AVERAGES =
        "SELECT DATE(e.date) AS d, AVG(t.value) AS a FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.date BETWEEN ? AND ? GROUP BY DATE(e.date) ORDER BY DATE(e.date)"

    /** Parameters: room id, kind. */
    const val AVERAGE =
        "SELECT AVG(t.value) AS a FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND e.kind = ?"

    fun inList(n: Int) = List(n) { "?" }.joinToString(", ")
}
