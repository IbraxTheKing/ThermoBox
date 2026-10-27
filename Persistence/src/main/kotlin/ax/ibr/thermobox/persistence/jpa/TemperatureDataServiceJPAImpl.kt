package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.LatestMeasurements
import ax.ibr.thermobox.persistence.TemperatureHistory
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDateTime

class TemperatureDataServiceJPAImpl(emf: EntityManagerFactory,
                                    entityClass: Class<Temperature>
) : TemperatureDataService, CrudJpaService<Temperature>(emf, entityClass) {

    override fun getByType(temperatureClass: Temperature): List<Temperature> = read { em ->
        em.createQuery(
            "SELECT t FROM Temperature t WHERE TYPE(t) = :type ORDER BY t.date DESC",
            Temperature::class.java
        ).setParameter("type", temperatureClass.javaClass)
            .resultList
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        val avg = read { em ->
            em.createQuery(
                "SELECT AVG(t.value) FROM Temperature t WHERE TYPE(t) = :type",
                java.lang.Double::class.java
            ).setParameter("type", temperatureClass.javaClass)
                .singleResult
        } ?: return null

        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    override fun update(t: Temperature) {
        transaction { em ->
            val before = Hourly.owner(em, t.id)
            // Its date may have changed: its device works its latest measurement out again
            t.id?.let { em.executeSql(LatestMeasurements.FORGET_ROOMS_OF_TEMPERATURE, it) }
            em.merge(t)
            em.flush()
            t.id?.let { em.executeSql(TemperatureHistory.COPY_BY_TEMPERATURE, kindOf(t), t.date, it) }
            Hourly.rebuild(em, listOf(before, Hourly.owner(em, t.id)))
        }
    }

    override fun remove(t: Temperature) {
        transaction { em ->
            t.id?.let { em.executeSql(LatestMeasurements.FORGET_TEMPERATURE, it) }
            val before = Hourly.owner(em, t.id)
            em.remove(em.merge(t))
            em.flush()
            Hourly.rebuild(em, listOf(before))
        }
    }

    override fun getPage(temperatureClass: Temperature?, offset: Int, limit: Int): List<Temperature> = read { em ->
        val query = if (temperatureClass == null) {
            em.createQuery("SELECT t FROM Temperature t ORDER BY t.date DESC, t.id DESC", Temperature::class.java)
        } else {
            em.createQuery("SELECT t FROM Temperature t WHERE TYPE(t) = :type ORDER BY t.date DESC, t.id DESC", Temperature::class.java)
                .setParameter("type", temperatureClass.javaClass)
        }
        query.setFirstResult(offset).setMaxResults(limit).resultList
    }

    override fun count(temperatureClass: Temperature?): Long = read { em ->
        val query = if (temperatureClass == null) {
            em.createQuery("SELECT COUNT(t) FROM Temperature t", Long::class.javaObjectType)
        } else {
            em.createQuery("SELECT COUNT(t) FROM Temperature t WHERE TYPE(t) = :type", Long::class.javaObjectType)
                .setParameter("type", temperatureClass.javaClass)
        }
        query.singleResult
    }
}
