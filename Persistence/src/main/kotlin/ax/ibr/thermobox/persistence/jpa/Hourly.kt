package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.persistence.HourlyMeasurements
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.LocalDateTime

/** Upkeep of `measurement_hourly` in a JPA transaction (see [HourlyMeasurements]). */
internal object Hourly {

    fun add(em: EntityManager, roomId: Long?, date: LocalDateTime?, value: Float?) {
        if (roomId == null || date == null || value == null) return
        em.executeSql(HourlyMeasurements.ADD, roomId, HourlyMeasurements.hourOf(date), value, value, value)
    }

    fun owner(em: EntityManager, temperatureId: Long?): Pair<Long, LocalDateTime>? {
        temperatureId ?: return null
        val row = em.querySql(HourlyMeasurements.OWNER, temperatureId).firstOrNull() ?: return null
        val room = (row[0] as Number?)?.toLong() ?: return null
        val date = when (val d = row[1]) {
            is Timestamp -> d.toLocalDateTime()
            is LocalDateTime -> d
            else -> null
        } ?: return null
        return room to date
    }

    fun rebuild(em: EntityManager, owners: Collection<Pair<Long, LocalDateTime>?>) {
        owners.filterNotNull().distinctBy { (r, d) -> r to HourlyMeasurements.hourOf(d) }.forEach { (roomId, date) ->
            val hour = HourlyMeasurements.hourOf(date)
            em.executeSql(HourlyMeasurements.FORGET, roomId, hour)
            em.executeSql(HourlyMeasurements.REBUILD, roomId, hour, hour.plusHours(1))
        }
    }
}
