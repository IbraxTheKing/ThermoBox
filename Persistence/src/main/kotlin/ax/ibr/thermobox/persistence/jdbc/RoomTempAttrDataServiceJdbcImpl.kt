package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.RoomTempAttrDataService
import ax.ibr.thermobox.persistence.jdbc.TemperatureDataServiceJdbcImpl.Companion.insertTemperature
import ax.ibr.thermobox.persistence.jdbc.TemperatureDataServiceJdbcImpl.Companion.updateTemperature
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Table `room_temperature_attr`: which element (a device, or a room / floor / building for its
 * setpoints) a temperature belongs to. As with JPA (cascade ALL), the temperature is saved and
 * deleted with its link.
 */
class RoomTempAttrDataServiceJdbcImpl(db: JdbcDatabase) : RoomTempAttrDataService, CrudJdbcService<RoomTempAttr>(db) {

    override val table = "room_temperature_attr"
    override val select = "SELECT e.id AS e_id, ${Rows.roomColumns("r")}, ${Rows.temperatureColumns("t")} " +
        "FROM room_temperature_attr e JOIN room r ON r.id = e.room_id JOIN temperature t ON t.id = e.temperature_id"

    override fun map(rs: ResultSet): RoomTempAttr? {
        val room = Rows.room(rs, "r") ?: return null
        val temperature = Rows.temperature(rs, "t") ?: return null
        return RoomTempAttr(room, temperature).apply { id = rs.getLong("e_id") }
    }

    override fun idOf(t: RoomTempAttr) = t.id
    override fun setId(t: RoomTempAttr, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: RoomTempAttr): Long {
        val temperature = t.temperature
        val temperatureId = temperature.id ?: insertTemperature(cx, temperature).also { temperature.id = it }
        return cx.insert("INSERT INTO room_temperature_attr (room_id, temperature_id) VALUES (?, ?)", saved(t.room.id), temperatureId)
    }

    override fun updateRow(cx: Connection, t: RoomTempAttr) {
        val temperature = t.temperature
        if (temperature.id == null) temperature.id = insertTemperature(cx, temperature) else updateTemperature(cx, temperature)
        cx.execute("UPDATE room_temperature_attr SET room_id = ?, temperature_id = ? WHERE id = ?", saved(t.room.id), temperature.id, t.id)
    }

    override fun deleteRow(cx: Connection, id: Long) {
        val temperatureId = cx.query("SELECT temperature_id FROM room_temperature_attr WHERE id = ?", id) { it.longOrNull("temperature_id") }.firstOrNull()
        cx.execute("DELETE FROM room_temperature_attr WHERE id = ?", id)
        if (temperatureId != null) cx.execute("DELETE FROM temperature WHERE id = ?", temperatureId)
    }

    override fun getByRoom(roomId: Long): List<RoomTempAttr>? =
        list("WHERE e.room_id = ? ORDER BY t.date DESC, t.id DESC", roomId).ifEmpty { null }

    override fun getByRoom(room: Room): List<RoomTempAttr>? = room.id?.let { getByRoom(it) }

    override fun getByTemperature(temperature: Temperature): RoomTempAttr? = temperature.id?.let { getByTemperature(it) }

    override fun getByTemperature(temperatureId: Long): RoomTempAttr? = list("WHERE e.temperature_id = ? LIMIT 1", temperatureId).firstOrNull()

    override fun getCurrentTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature? {
        val roomId = room.id ?: return null
        return db.read { cx -> latest(cx, roomId, Rows.temperatureType(temperatureClass)) }
    }

    /** The latest temperature of each kind (setpoint, measurement) of [room]. */
    override fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? {
        val roomId = room.id ?: return null
        return db.read { cx ->
            cx.query("SELECT DISTINCT t.DTYPE AS k FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id WHERE e.room_id = ?", roomId) {
                it.getString("k")
            }.mapNotNull { latest(cx, roomId, it) }
        }.ifEmpty { null }
    }

    override fun getSetpointFromRoom(room: Room): Setpoint? {
        val roomId = room.id ?: return null
        return db.read { cx -> latest(cx, roomId, Rows.temperatureType(Setpoint::class.java)) } as? Setpoint
    }

    override fun getTemperaturesFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return timed(roomId, Rows.temperatureType(Measurement::class.java), start, end)
    }

    override fun getSetpointsFromRoomTimed(room: Room, start: Instant, end: Instant): List<Setpoint> {
        val roomId = room.id ?: return emptyList()
        return timed(roomId, Rows.temperatureType(Setpoint::class.java), start, end).filterIsInstance<Setpoint>()
    }

    override fun getAverageTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature? {
        val roomId = room.id ?: return null
        val avg = db.read { cx ->
            cx.query(
                "SELECT AVG(t.value) AS a FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id WHERE e.room_id = ? AND t.DTYPE = ?",
                roomId, Rows.temperatureType(temperatureClass),
            ) { rs -> rs.getDouble("a").takeUnless { rs.wasNull() } }.firstOrNull()
        } ?: return null
        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    /** Average of every temperature of [room] per day of the period, the oldest day first. */
    override fun getAverageTemperatureFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return db.read { cx ->
            cx.query(
                "SELECT DATE(t.date) AS d, AVG(t.value) AS a FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
                    "WHERE e.room_id = ? AND t.date BETWEEN ? AND ? GROUP BY DATE(t.date) ORDER BY DATE(t.date)",
                roomId, start.toLocal(), end.toLocal(),
            ) { rs -> rs.getDate("d")?.let { Temperature(rs.getFloat("a"), it.toLocalDate().atStartOfDay()) } }
        }
    }

    override fun getAllByTime(start: Instant, end: Instant): List<RoomTempAttr> =
        list("WHERE t.date BETWEEN ? AND ? ORDER BY t.date, t.id", start.toLocal(), end.toLocal())

    private fun latest(cx: Connection, roomId: Long, type: String): Temperature? = cx.query(
        "SELECT ${Rows.temperatureColumns("t")} FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
            "WHERE e.room_id = ? AND t.DTYPE = ? ORDER BY t.date DESC, t.id DESC LIMIT 1",
        roomId, type,
    ) { Rows.temperature(it, "t") }.firstOrNull()

    private fun timed(roomId: Long, type: String, start: Instant, end: Instant): List<Temperature> = db.read { cx ->
        cx.query(
            "SELECT ${Rows.temperatureColumns("t")} FROM room_temperature_attr e JOIN temperature t ON t.id = e.temperature_id " +
                "WHERE e.room_id = ? AND t.DTYPE = ? AND t.date BETWEEN ? AND ? ORDER BY t.date, t.id",
            roomId, type, start.toLocal(), end.toLocal(),
        ) { Rows.temperature(it, "t") }
    }

    // Instant (UTC, from the API) -> LocalDateTime (no time zone, as stored in the database)
    private fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
}
