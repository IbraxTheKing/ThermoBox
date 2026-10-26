package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
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

    override fun getCurrentTemperatureFromRoom(
        room: Room,
        temperatureClass: Temperature
    ): Temperature? {
        val roomId = room.id ?: return null
        return read { em ->
            em.createQuery(
                "SELECT sta.temperature FROM RoomTempAttr sta " +
                        "WHERE sta.room.id = :roomId AND TYPE(sta.temperature) = :type " +
                        "ORDER BY sta.temperature.date DESC",
                Temperature::class.java
            ).setParameter("roomId", roomId)
                .setParameter("type", temperatureClass.javaClass)
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
