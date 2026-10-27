package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.HourlyMeasurements
import ax.ibr.thermobox.persistence.LatestMeasurements
import ax.ibr.thermobox.persistence.TemperatureHistory
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
        val id = cx.insert(
            "INSERT INTO room_temperature_attr (room_id, temperature_id, kind, date) VALUES (?, ?, ?, ?)",
            saved(t.room.id), temperatureId, Rows.temperatureType(temperature), temperature.date,
        )
        if (temperature is Measurement && temperature.date != null) {
            cx.execute(LatestMeasurements.UPSERT, t.room.id, temperatureId, temperature.date)
            Hourly.add(cx, t.room.id, temperature.date, temperature.value)
        }
        return id
    }

    override fun updateRow(cx: Connection, t: RoomTempAttr) {
        val temperature = t.temperature
        // Where the measurement counted before the change (device, hour)
        val before = Hourly.owner(cx, temperature.id)
        if (temperature.id == null) temperature.id = insertTemperature(cx, temperature) else updateTemperature(cx, temperature)
        // The devices concerned (the one it was on, the one it goes to) work their latest out again
        cx.execute(LatestMeasurements.FORGET_ROOMS_OF_TEMPERATURE, temperature.id)
        cx.execute(LatestMeasurements.FORGET_ROOM, t.room.id)
        cx.execute(
            "UPDATE room_temperature_attr SET room_id = ?, temperature_id = ?, kind = ?, date = ? WHERE id = ?",
            saved(t.room.id), temperature.id, Rows.temperatureType(temperature), temperature.date, t.id,
        )
        Hourly.rebuild(cx, listOf(before, Hourly.owner(cx, temperature.id)))
    }

    override fun deleteRow(cx: Connection, id: Long) {
        val temperatureId = cx.query("SELECT temperature_id FROM room_temperature_attr WHERE id = ?", id) { it.longOrNull("temperature_id") }.firstOrNull()
        if (temperatureId != null) cx.execute(LatestMeasurements.FORGET_TEMPERATURE, temperatureId)
        val before = Hourly.owner(cx, temperatureId)
        cx.execute("DELETE FROM room_temperature_attr WHERE id = ?", id)
        if (temperatureId != null) cx.execute("DELETE FROM temperature WHERE id = ?", temperatureId)
        Hourly.rebuild(cx, listOf(before))
    }

    override fun getByRoom(roomId: Long): List<RoomTempAttr>? =
        list("WHERE e.room_id = ? ORDER BY e.date DESC, e.temperature_id DESC", roomId).ifEmpty { null }

    override fun getByRoom(room: Room): List<RoomTempAttr>? = room.id?.let { getByRoom(it) }

    override fun getByTemperature(temperature: Temperature): RoomTempAttr? = temperature.id?.let { getByTemperature(it) }

    override fun getByTemperature(temperatureId: Long): RoomTempAttr? = list("WHERE e.temperature_id = ? LIMIT 1", temperatureId).firstOrNull()

    override fun getCurrentTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature? {
        val roomId = room.id ?: return null
        if (temperatureClass is Measurement) return getLatestMeasurements(listOf(room))[roomId]
        return db.read { cx -> latest(cx, roomId, Rows.temperatureType(temperatureClass)) }
    }

    /** From `latest_measurement`; a device without a row there gets it worked out and saved. */
    override fun getLatestMeasurements(devices: Collection<Room>): Map<Long, Temperature> {
        val ids = devices.mapNotNull { it.id }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, Temperature>()
        db.read { cx ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                cx.query(
                    "SELECT l.room_id AS d, ${Rows.temperatureColumns("t")} FROM latest_measurement l " +
                        "JOIN temperature t ON t.id = l.temperature_id WHERE l.room_id IN (${placeholders(part.size)})",
                    *part.toTypedArray(),
                ) { rs -> Rows.temperature(rs, "t")?.let { rs.getLong("d") to it } }.forEach { (d, t) -> result[d] = t }
            }
        }
        val measurement = Rows.temperatureType(Measurement::class.java)
        for (id in ids.filter { it !in result }) {
            db.transaction { cx ->
                latest(cx, id, measurement)?.let { t ->
                    result[id] = t
                    if (t.date != null) cx.execute(LatestMeasurements.UPSERT, id, t.id, t.date)
                }
            }
        }
        return result
    }

    /** Own setpoints only, of the elements given (buildings, floors, rooms have a few each). */
    override fun getLatestSetpoints(elements: Collection<Room>): Map<Long, Setpoint> {
        val ids = elements.mapNotNull { it.id }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, Setpoint>()
        db.read { cx ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                cx.query(
                    TemperatureHistory.ofRooms(part.size),
                    Rows.temperatureType(Setpoint::class.java), *part.toTypedArray(),
                ) { rs -> (Rows.temperature(rs, "t") as? Setpoint)?.let { rs.getLong("r") to it } }.forEach { (r, s) ->
                    val kept = result[r]
                    if (kept == null || newer(s, kept)) result[r] = s
                }
            }
        }
        return result
    }

    override fun getRoomIdsOfTemperatures(temperatureIds: Collection<Long>): Map<Long, Long> {
        val ids = temperatureIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return db.read { cx ->
            ids.chunked(LatestMeasurements.CHUNK).flatMap { part ->
                cx.query(
                    "SELECT temperature_id, room_id FROM room_temperature_attr WHERE temperature_id IN (${placeholders(part.size)})",
                    *part.toTypedArray(),
                ) { rs -> rs.longOrNull("room_id")?.let { rs.getLong("temperature_id") to it } }
            }
        }.toMap()
    }

    override fun getPage(offset: Int, limit: Int): List<RoomTempAttr> =
        list("ORDER BY t.date DESC, t.id DESC LIMIT ? OFFSET ?", limit, offset)

    /** Later date, or same date and saved later (the order of the query that picks "the latest"). */
    private fun newer(a: Temperature, b: Temperature): Boolean {
        val da = a.date ?: LocalDateTime.MIN
        val dbDate = b.date ?: LocalDateTime.MIN
        return da.isAfter(dbDate) || (da == dbDate && (a.id ?: 0) > (b.id ?: 0))
    }

    /** The latest temperature of each kind (setpoint, measurement) of [room]. */
    override fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? {
        val roomId = room.id ?: return null
        return db.read { cx ->
            cx.query("SELECT DISTINCT kind AS k FROM room_temperature_attr WHERE room_id = ? AND kind IS NOT NULL", roomId) {
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
                TemperatureHistory.AVERAGE,
                roomId, Rows.temperatureType(temperatureClass),
            ) { rs -> rs.getDouble("a").takeUnless { rs.wasNull() } }.firstOrNull()
        } ?: return null
        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    /** Average of every temperature of [room] per day of the period, the oldest day first. */
    override fun getAverageTemperatureFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return db.read { cx ->
            cx.query(TemperatureHistory.DAILY_AVERAGES, roomId, start.toLocal(), end.toLocal()) { rs ->
                rs.getDate("d")?.let { Temperature(rs.getFloat("a"), it.toLocalDate().atStartOfDay()) }
            }
        }
    }

    /** Hourly averages of every device of [devices] over the period (measurement_hourly). */
    override fun getHourlyMeasurementsOf(devices: Collection<Room>, start: Instant, end: Instant): Map<Long, List<Temperature>> {
        val ids = devices.mapNotNull { it.id }.distinct()
        val result = LinkedHashMap<Long, MutableList<Temperature>>()
        ids.forEach { result[it] = ArrayList() }
        if (ids.isEmpty()) return result
        val from = HourlyMeasurements.hourOf(start.toLocal())
        db.read { cx ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                cx.query(HourlyMeasurements.averages(part.size), from, end.toLocal(), *part.toTypedArray()) { rs ->
                    val hour = rs.dateTime("hour") ?: return@query null
                    rs.getLong("room_id") to Measurement(rs.getDouble("average").toFloat(), hour.plusMinutes(30))
                }.forEach { (r, t) -> result.getValue(r).add(t) }
            }
        }
        return result
    }

    /** The measurements of every device of [devices] over the period, in one query per thousand devices. */
    override fun getMeasurementsOf(devices: Collection<Room>, start: Instant, end: Instant): Map<Long, List<Temperature>> {
        val ids = devices.mapNotNull { it.id }.distinct()
        val result = LinkedHashMap<Long, MutableList<Temperature>>()
        ids.forEach { result[it] = ArrayList() }
        if (ids.isEmpty()) return result
        val kind = Rows.temperatureType(Measurement::class.java)
        db.read { cx ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                cx.query(TemperatureHistory.periodOfRooms(part.size), kind, start.toLocal(), end.toLocal(), *part.toTypedArray()) { rs ->
                    Rows.temperature(rs, "t")?.let { rs.getLong("r") to it }
                }.forEach { (r, t) -> result.getValue(r).add(t) }
            }
        }
        return result
    }

    override fun getAllByTime(start: Instant, end: Instant): List<RoomTempAttr> =
        list("WHERE e.date BETWEEN ? AND ? ORDER BY e.date, e.temperature_id", start.toLocal(), end.toLocal())

    private fun latest(cx: Connection, roomId: Long, type: String): Temperature? =
        cx.query(TemperatureHistory.LATEST, roomId, type) { Rows.temperature(it, "t") }.firstOrNull()

    private fun timed(roomId: Long, type: String, start: Instant, end: Instant): List<Temperature> = db.read { cx ->
        cx.query(TemperatureHistory.PERIOD, roomId, type, start.toLocal(), end.toLocal()) { Rows.temperature(it, "t") }
    }

    // Instant (UTC, from the API) -> LocalDateTime (no time zone, as stored in the database)
    private fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
}
