package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.LatestMeasurements
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
                "SELECT sta FROM RoomTempAttr sta WHERE sta.room.id = :roomId ORDER BY sta.temperature.date DESC",
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
            if (temperature is Measurement && temperature.date != null) {
                em.executeSql(LatestMeasurements.UPSERT, t.room.id, temperature.id, temperature.date)
            }
        }
    }

    override fun update(t: RoomTempAttr) {
        transaction { em ->
            t.temperature.id?.let { em.executeSql(LatestMeasurements.FORGET_ROOMS_OF_TEMPERATURE, it) }
            t.room.id?.let { em.executeSql(LatestMeasurements.FORGET_ROOM, it) }
            em.merge(t)
        }
    }

    override fun remove(t: RoomTempAttr) {
        transaction { em ->
            t.temperature.id?.let { em.executeSql(LatestMeasurements.FORGET_TEMPERATURE, it) }
            em.remove(em.merge(t))
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
                em.createQuery(
                    "SELECT sta FROM RoomTempAttr sta WHERE TYPE(sta.temperature) = Setpoint AND sta.room.id IN :ids",
                    RoomTempAttr::class.java
                ).setParameter("ids", part).resultList.forEach { a ->
                    val id = a.room.id ?: return@forEach
                    val s = a.temperature as? Setpoint ?: return@forEach
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

    /** The latest temperature of [type] among the whole history of [roomId]. */
    private fun latestFromHistory(roomId: Long, type: Class<out Temperature>): Temperature? {
        return read { em ->
            em.createQuery(
                "SELECT sta.temperature FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = :type " +
                        "ORDER BY sta.temperature.date DESC, sta.temperature.id DESC",
                Temperature::class.java
            ).setParameter("roomId", roomId)
                .setParameter("type", type)
                .setMaxResults(1)
                .resultList
                .firstOrNull()
        }
    }

    override fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? {
        val roomId = room.id ?: return null

        val result = read { em ->
            val types = em.createQuery(
                "SELECT DISTINCT TYPE(sta.temperature) FROM RoomTempAttr sta WHERE sta.room.id = :roomId",
                Class::class.java
            ).setParameter("roomId", roomId)
                .resultList

            types.mapNotNull { type ->
                em.createQuery(
                    "SELECT sta.temperature FROM RoomTempAttr sta " +
                            "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = :type " +
                            "ORDER BY sta.temperature.date DESC",
                    Temperature::class.java
                ).setParameter("roomId", roomId)
                    .setParameter("type", type)
                    .setMaxResults(1)
                    .resultList
                    .firstOrNull()
            }
        }
        return result.ifEmpty { null }
    }

    override fun getSetpointFromRoom(room: Room): Setpoint? {
        val roomId = room.id ?: return null
        return read { em ->
            em.createQuery(
                "SELECT sta.temperature FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = Setpoint " +
                        "ORDER BY sta.temperature.date DESC",
                Setpoint::class.java
            ).setParameter("roomId", roomId)
                .setMaxResults(1)
                .resultList
                .firstOrNull()
        }
    }

    override fun getTemperaturesFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT sta.temperature FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = Measurement " +
                        "AND sta.temperature.date BETWEEN :start AND :end " +
                        "ORDER BY sta.temperature.date ASC",
                Temperature::class.java
            ).setParameter("roomId", roomId)
                .setParameter("start", start.toLocal())
                .setParameter("end", end.toLocal())
                .resultList
        }
    }

    override fun getSetpointsFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Setpoint> {
        val roomId = room.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT sta.temperature FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = Setpoint " +
                        "AND sta.temperature.date BETWEEN :start AND :end " +
                        "ORDER BY sta.temperature.date ASC",
                Setpoint::class.java
            ).setParameter("roomId", roomId)
                .setParameter("start", start.toLocal())
                .setParameter("end", end.toLocal())
                .resultList
        }
    }

    override fun getAverageTemperatureFromRoom(
        room: Room,
        temperatureClass: Temperature
    ): Temperature? {
        val roomId = room.id ?: return null
        val avg = read { em ->
            em.createQuery(
                "SELECT AVG(sta.temperature.value) FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = :type",
                java.lang.Double::class.java
            ).setParameter("roomId", roomId)
                .setParameter("type", temperatureClass.javaClass)
                .singleResult
        } ?: return null

        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    override fun getAverageTemperatureFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        val roomId = room.id ?: return emptyList()
        val rows = read { em ->
            em.createQuery(
                "SELECT FUNCTION('DATE', sta.temperature.date), AVG(sta.temperature.value) " +
                        "FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId " +
                        "AND sta.temperature.date BETWEEN :start AND :end " +
                        "GROUP BY FUNCTION('DATE', sta.temperature.date) " +
                        "ORDER BY FUNCTION('DATE', sta.temperature.date) ASC",
                Array<Any>::class.java
            ).setParameter("roomId", roomId)
                .setParameter("start", start.toLocal())
                .setParameter("end", end.toLocal())
                .resultList
        }

        return rows.map { row ->
            val day = row[0] as java.sql.Date
            val avg = (row[1] as Number).toFloat()
            Temperature(avg, day.toLocalDate().atStartOfDay())
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
