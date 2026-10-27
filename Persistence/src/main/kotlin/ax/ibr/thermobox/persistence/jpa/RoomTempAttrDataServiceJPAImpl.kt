package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.HourlyMeasurements
import ax.ibr.thermobox.persistence.LatestMeasurements
import ax.ibr.thermobox.persistence.TemperatureHistory
import ax.ibr.thermobox.persistence.dataservices.RoomTempAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RoomTempAttrDataServiceJPAImpl(emf: EntityManagerFactory,
                                      entityClass: Class<RoomTempAttr>
) : RoomTempAttrDataService, CrudJpaService<RoomTempAttr>(emf, entityClass) {

    // Instant (UTC, from the API) -> LocalDateTime (no time zone, as stored in the database)
    private fun Instant.toLocal(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneId.systemDefault())

    override fun getByRoom(roomId: Long): List<RoomTempAttr>? {
        val result = read { em ->
            em.createQuery(
                "SELECT sta FROM RoomTempAttr sta WHERE sta.room.id = :roomId ORDER BY sta.temperature.date DESC, sta.temperature.id DESC",
                RoomTempAttr::class.java
            ).setParameter("roomId", roomId)
                .resultList
        }
        return result.ifEmpty { null }
    }

    override fun getByRoom(room: Room): List<RoomTempAttr>? {
        val roomId = room.id ?: return null
        return getByRoom(roomId)
    }

    override fun getByTemperature(temperature: Temperature): RoomTempAttr? {
        val temperatureId = temperature.id ?: return null
        return getByTemperature(temperatureId)
    }

    override fun getByTemperature(temperatureId: Long): RoomTempAttr? = read { em ->
        em.createQuery(
            "SELECT sta FROM RoomTempAttr sta WHERE sta.temperature.id = :temperatureId",
            RoomTempAttr::class.java
        ).setParameter("temperatureId", temperatureId)
            .resultList
            .firstOrNull()
    }

    // --- latest_measurement (see LatestMeasurements): kept up to date on every write ---

    override fun add(t: RoomTempAttr) {
        transaction { em ->
            em.persist(t)
            em.flush()
            val temperature = t.temperature
            // Kind and date next to the link: the history of an element is read through one index
            em.executeSql(TemperatureHistory.COPY_BY_LINK, kindOf(temperature), temperature.date, t.id)
            if (temperature is Measurement && temperature.date != null) {
                em.executeSql(LatestMeasurements.UPSERT, t.room.id, temperature.id, temperature.date)
                Hourly.add(em, t.room.id, temperature.date, temperature.value)
            }
        }
    }

    override fun update(t: RoomTempAttr) {
        transaction { em ->
            val before = Hourly.owner(em, t.temperature.id)
            t.temperature.id?.let { em.executeSql(LatestMeasurements.FORGET_ROOMS_OF_TEMPERATURE, it) }
            t.room.id?.let { em.executeSql(LatestMeasurements.FORGET_ROOM, it) }
            val merged = em.merge(t)
            em.flush()
            em.executeSql(TemperatureHistory.COPY_BY_LINK, kindOf(t.temperature), t.temperature.date, merged.id)
            Hourly.rebuild(em, listOf(before, Hourly.owner(em, merged.temperature.id)))
        }
    }

    override fun remove(t: RoomTempAttr) {
        transaction { em ->
            t.temperature.id?.let { em.executeSql(LatestMeasurements.FORGET_TEMPERATURE, it) }
            val before = Hourly.owner(em, t.temperature.id)
            em.remove(em.merge(t))
            em.flush()
            Hourly.rebuild(em, listOf(before))
        }
    }

    override fun getCurrentTemperatureFromRoom(
        room: Room,
        temperatureClass: Temperature
    ): Temperature? {
        val roomId = room.id ?: return null
        if (temperatureClass is Measurement) return getLatestMeasurements(listOf(room))[roomId]
        return latestFromHistory(roomId, temperatureClass.javaClass)
    }

    /** From `latest_measurement`; a device without a row there gets it worked out and saved. */
    override fun getLatestMeasurements(devices: Collection<Room>): Map<Long, Temperature> {
        val ids = devices.mapNotNull { it.id }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, Temperature>()
        read { em ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                @Suppress("UNCHECKED_CAST")
                val rows = em.createNativeQuery(
                    "SELECT room_id, temperature_id FROM latest_measurement WHERE room_id IN (" +
                        part.indices.joinToString(", ") { "?${it + 1}" } + ")"
                ).also { q -> part.forEachIndexed { i, id -> q.setParameter(i + 1, id) } }.resultList as List<Array<Any?>>
                val byTemperature = rows.associate { (it[1] as Number).toLong() to (it[0] as Number).toLong() }
                if (byTemperature.isEmpty()) continue
                em.createQuery("SELECT t FROM Temperature t WHERE t.id IN :ids", Temperature::class.java)
                    .setParameter("ids", byTemperature.keys.toList())
                    .resultList
                    .forEach { t -> byTemperature[t.id]?.let { result[it] = t } }
            }
        }
        for (id in ids.filter { it !in result }) {
            val t = latestFromHistory(id, Measurement::class.java) ?: continue
            result[id] = t
            if (t.date != null) transaction { em -> em.executeSql(LatestMeasurements.UPSERT, id, t.id, t.date) }
        }
        return result
    }

    /** Own setpoints only, of the elements given (buildings, floors, rooms have a few each). */
    override fun getLatestSetpoints(elements: Collection<Room>): Map<Long, Setpoint> {
        val ids = elements.mapNotNull { it.id }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, Setpoint>()
        read { em ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                em.querySql(TemperatureHistory.ofRooms(part.size), kindOf(Setpoint::class.java), *part.toTypedArray()).forEach { row ->
                    val id = (row[0] as Number).toLong()
                    val s = temperatureOf(row, 1) as? Setpoint ?: return@forEach
                    val kept = result[id]
                    if (kept == null || newer(s, kept)) result[id] = s
                }
            }
        }
        return result
    }

    override fun getRoomIdsOfTemperatures(temperatureIds: Collection<Long>): Map<Long, Long> {
        val ids = temperatureIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return read { em ->
            ids.chunked(LatestMeasurements.CHUNK).flatMap { part ->
                em.createQuery(
                    "SELECT sta.temperature.id, sta.room.id FROM RoomTempAttr sta WHERE sta.temperature.id IN :ids",
                    Array<Any>::class.java
                ).setParameter("ids", part).resultList.map { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
            }
        }.toMap()
    }

    override fun getPage(offset: Int, limit: Int): List<RoomTempAttr> = read { em ->
        em.createQuery(
            "SELECT sta FROM RoomTempAttr sta ORDER BY sta.temperature.date DESC, sta.temperature.id DESC",
            RoomTempAttr::class.java
        ).setFirstResult(offset).setMaxResults(limit).resultList
    }

    private fun newer(a: Temperature, b: Temperature): Boolean {
        val da = a.date ?: LocalDateTime.MIN
        val dbDate = b.date ?: LocalDateTime.MIN
        return da.isAfter(dbDate) || (da == dbDate && (a.id ?: 0) > (b.id ?: 0))
    }

    /** The latest temperature of [type] in the history of [roomId] (index room, kind, date). */
    private fun latestFromHistory(roomId: Long, type: Class<out Temperature>): Temperature? = read { em ->
        em.querySql(TemperatureHistory.LATEST, roomId, kindOf(type)).firstOrNull()?.let { temperatureOf(it) }
    }

    override fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? {
        val roomId = room.id ?: return null
        val kinds = read { em ->
            em.querySql("SELECT DISTINCT kind FROM room_temperature_attr WHERE room_id = ? AND kind IS NOT NULL", roomId)
                .mapNotNull { it[0] as String? }
        }
        return kinds.mapNotNull { kind ->
            if (kind == "Measurement") getLatestMeasurements(listOf(room))[roomId]
            else read { em -> em.querySql(TemperatureHistory.LATEST, roomId, kind).firstOrNull()?.let { temperatureOf(it) } }
        }.ifEmpty { null }
    }

    override fun getSetpointFromRoom(room: Room): Setpoint? {
        val roomId = room.id ?: return null
        return latestFromHistory(roomId, Setpoint::class.java) as? Setpoint
    }

    override fun getTemperaturesFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return period(roomId, Measurement::class.java, start, end)
    }

    override fun getSetpointsFromRoomTimed(room: Room, start: Instant, end: Instant): List<Setpoint> {
        val roomId = room.id ?: return emptyList()
        return period(roomId, Setpoint::class.java, start, end).filterIsInstance<Setpoint>()
    }

    /** Plain values read through the index (room, kind, date): no entity per point. */
    private fun period(roomId: Long, type: Class<out Temperature>, start: Instant, end: Instant): List<Temperature> = read { em ->
        em.querySql(TemperatureHistory.PERIOD, roomId, kindOf(type), start.toLocal(), end.toLocal()).map { temperatureOf(it) }
    }

    /** Hourly averages of every device of [devices] over the period (measurement_hourly). */
    override fun getHourlyMeasurementsOf(devices: Collection<Room>, start: Instant, end: Instant): Map<Long, List<Temperature>> {
        val ids = devices.mapNotNull { it.id }.distinct()
        val result = LinkedHashMap<Long, MutableList<Temperature>>()
        ids.forEach { result[it] = ArrayList() }
        if (ids.isEmpty()) return result
        val from = HourlyMeasurements.hourOf(start.toLocal())
        read { em ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                em.querySql(HourlyMeasurements.averages(part.size), from, end.toLocal(), *part.toTypedArray()).forEach { row ->
                    val hour = when (val d = row[1]) {
                        is java.sql.Timestamp -> d.toLocalDateTime()
                        is LocalDateTime -> d
                        else -> return@forEach
                    }
                    result.getValue((row[0] as Number).toLong()).add(Measurement((row[2] as Number).toFloat(), hour.plusMinutes(30)))
                }
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
        read { em ->
            for (part in ids.chunked(LatestMeasurements.CHUNK)) {
                em.querySql(TemperatureHistory.periodOfRooms(part.size), kindOf(Measurement::class.java), start.toLocal(), end.toLocal(), *part.toTypedArray())
                    .forEach { row -> result.getValue((row[0] as Number).toLong()).add(temperatureOf(row, 1)) }
            }
        }
        return result
    }

    override fun getAverageTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature? {
        val roomId = room.id ?: return null
        val avg = read { em -> em.querySql(TemperatureHistory.AVERAGE, roomId, kindOf(temperatureClass)).firstOrNull()?.get(0) as Number? }
            ?: return null
        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    override fun getAverageTemperatureFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return read { em -> em.querySql(TemperatureHistory.DAILY_AVERAGES, roomId, start.toLocal(), end.toLocal()) }.mapNotNull { row ->
            val day = when (val d = row[0]) {
                is java.sql.Date -> d.toLocalDate()
                is java.time.LocalDate -> d
                else -> null
            } ?: return@mapNotNull null
            Temperature((row[1] as Number).toFloat(), day.atStartOfDay())
        }
    }

    override fun getAllByTime(
        start: Instant,
        end: Instant
    ): List<RoomTempAttr> {
        val zone = ZoneId.systemDefault()
        return read { em ->
            em.createQuery(
                """
            SELECT sta FROM RoomTempAttr sta
            JOIN FETCH sta.temperature t
            JOIN FETCH sta.room
            WHERE t.date BETWEEN :start AND :end
            ORDER BY t.date
            """.trimIndent(),
                RoomTempAttr::class.java
            )
                .setParameter("start", LocalDateTime.ofInstant(start, zone))
                .setParameter("end", LocalDateTime.ofInstant(end, zone))
                .resultList
        }
    }
}
