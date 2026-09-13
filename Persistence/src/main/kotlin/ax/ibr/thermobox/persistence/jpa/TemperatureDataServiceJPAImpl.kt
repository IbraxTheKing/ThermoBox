package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager
import java.sql.Date
import java.time.LocalDateTime

class TemperatureDataServiceJPAImpl(pu: String, em: EntityManager,
                                    entityClass: Class<Temperature>
) : TemperatureDataService, CrudJpaService<Temperature>(em, entityClass) {

    override fun getByType(temperatureClass: Temperature): List<Temperature> {
        return em.createQuery(
            "SELECT t FROM Temperature t WHERE TYPE(t) = :type ORDER BY t.date DESC",
            Temperature::class.java
        ).setParameter("type", temperatureClass.javaClass)
            .resultList
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        val avg = em.createQuery(
            "SELECT AVG(t.value) FROM Temperature t WHERE TYPE(t) = :type",
            java.lang.Double::class.java
        ).setParameter("type", temperatureClass.javaClass)
            .singleResult ?: return null

        return Temperature(avg.toFloat(), LocalDateTime.now())
    }
}