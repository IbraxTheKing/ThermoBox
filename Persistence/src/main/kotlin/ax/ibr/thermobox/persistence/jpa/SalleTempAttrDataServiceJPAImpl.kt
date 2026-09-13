package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SalleTempAttrDataServiceJPAImpl(pu: String, em: EntityManager,
                                      entityClass: Class<SalleTempAttr>
) : SalleTempAttrDataService, CrudJpaService<SalleTempAttr>(em, entityClass) {

    // Instant (UTC, venant de l'API) -> LocalDateTime (sans fuseau, tel que stocké en base)
    private fun Instant.toLocal(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneId.systemDefault())

    override fun getBySalle(salleId: Long): List<SalleTempAttr>? {
        val result = em.createQuery(
            "SELECT sta FROM SalleTempAttr sta WHERE sta.salle.id = :salleId ORDER BY sta.temperature.date DESC",
            SalleTempAttr::class.java
        ).setParameter("salleId", salleId)
            .resultList
        return result.ifEmpty { null }
    }

    override fun getBySalle(salle: Salle): List<SalleTempAttr>? {
        val salleId = salle.id ?: return null
        return getBySalle(salleId)
    }

    override fun getByTemperature(temperature: Temperature): SalleTempAttr? {
        val temperatureId = temperature.id ?: return null
        return getByTemperature(temperatureId)
    }

    override fun getByTemperature(temperatureId: Long): SalleTempAttr? {
        return em.createQuery(
            "SELECT sta FROM SalleTempAttr sta WHERE sta.temperature.id = :temperatureId",
            SalleTempAttr::class.java
        ).setParameter("temperatureId", temperatureId)
            .resultList
            .firstOrNull()
    }

    override fun getCurrentTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        val salleId = salle.id ?: return null
        return em.createQuery(
            "SELECT sta.temperature FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = :type " +
                    "ORDER BY sta.temperature.date DESC",
            Temperature::class.java
        ).setParameter("salleId", salleId)
            .setParameter("type", temperatureClass.javaClass)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
    }

    override fun getCurrentTemperaturesFromSalle(salle: Salle): List<Temperature>? {
        val salleId = salle.id ?: return null

        val types = em.createQuery(
            "SELECT DISTINCT TYPE(sta.temperature) FROM SalleTempAttr sta WHERE sta.salle.id = :salleId",
            Class::class.java
        ).setParameter("salleId", salleId)
            .resultList

        val result = types.mapNotNull { type ->
            em.createQuery(
                "SELECT sta.temperature FROM SalleTempAttr sta " +
                        "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = :type " +
                        "ORDER BY sta.temperature.date DESC",
                Temperature::class.java
            ).setParameter("salleId", salleId)
                .setParameter("type", type)
                .setMaxResults(1)
                .resultList
                .firstOrNull()
        }
        return result.ifEmpty { null }
    }

    override fun getConsigneFromSalle(salle: Salle): Consigne? {
        val salleId = salle.id ?: return null
        return em.createQuery(
            "SELECT sta.temperature FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = Consigne " +
                    "ORDER BY sta.temperature.date DESC",
            Consigne::class.java
        ).setParameter("salleId", salleId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()
    }

    override fun getTemperaturesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        val salleId = salle.id ?: return emptyList()
        return em.createQuery(
            "SELECT sta.temperature FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = Mesurer " +
                    "AND sta.temperature.date BETWEEN :start AND :end " +
                    "ORDER BY sta.temperature.date ASC",
            Temperature::class.java
        ).setParameter("salleId", salleId)
            .setParameter("start", start.toLocal())
            .setParameter("end", end.toLocal())
            .resultList
    }

    override fun getConsignesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Consigne> {
        val salleId = salle.id ?: return emptyList()
        return em.createQuery(
            "SELECT sta.temperature FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = Consigne " +
                    "AND sta.temperature.date BETWEEN :start AND :end " +
                    "ORDER BY sta.temperature.date ASC",
            Consigne::class.java
        ).setParameter("salleId", salleId)
            .setParameter("start", start.toLocal())
            .setParameter("end", end.toLocal())
            .resultList
    }

    override fun getAverageTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        val salleId = salle.id ?: return null
        val avg = em.createQuery(
            "SELECT AVG(sta.temperature.value) FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId AND TYPE(sta.temperature) = :type",
            java.lang.Double::class.java
        ).setParameter("salleId", salleId)
            .setParameter("type", temperatureClass.javaClass)
            .singleResult ?: return null

        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    override fun getAverageTemperatureFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        val salleId = salle.id ?: return emptyList()
        val rows = em.createQuery(
            "SELECT FUNCTION('DATE', sta.temperature.date), AVG(sta.temperature.value) " +
                    "FROM SalleTempAttr sta " +
                    "WHERE sta.salle.id = :salleId " +
                    "AND sta.temperature.date BETWEEN :start AND :end " +
                    "GROUP BY FUNCTION('DATE', sta.temperature.date) " +
                    "ORDER BY FUNCTION('DATE', sta.temperature.date) ASC",
            Array<Any>::class.java
        ).setParameter("salleId", salleId)
            .setParameter("start", start.toLocal())
            .setParameter("end", end.toLocal())
            .resultList

        return rows.map { row ->
            val day = row[0] as java.sql.Date
            val avg = (row[1] as Number).toFloat()
            Temperature(avg, day.toLocalDate().atStartOfDay())
        }
    }
}