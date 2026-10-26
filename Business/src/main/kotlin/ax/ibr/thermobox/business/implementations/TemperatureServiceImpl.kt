package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.rules.SetpointRules
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

// The data service is a constructor parameter so tests can pass a mock.
class TemperatureServiceImpl(
    private val temperatureService: TemperatureService = PersistenceFactory.getTemperatureDataService()
) : TemperatureService, Serializable {

    override fun getByType(temperatureClass: Temperature): List<Temperature>? {
        return temperatureService.getByType(temperatureClass)
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        return temperatureService.getAverage(temperatureClass)
    }

    /** Refuses a value outside the limits chosen by an ADMIN (Celsius, as every stored temperature). */
    override fun add(t: Temperature) {
        t.value?.let { if (t is Setpoint) SetpointRules.check(it) else SetpointRules.checkMeasurement(it) }
        temperatureService.add(t)
    }

    override fun update(t: Temperature) {
        temperatureService.update(t)
    }

    override fun remove(t: Temperature) {
        temperatureService.remove(t)
    }

    override fun getAll(): List<Temperature> {
        return temperatureService.getAll()
    }

    override fun getById(id: Long): Temperature? {
        return temperatureService.getById(id)
    }

    override fun getPage(temperatureClass: Temperature?, offset: Int, limit: Int): List<Temperature> =
        temperatureService.getPage(temperatureClass, offset, limit)

    override fun count(temperatureClass: Temperature?): Long = temperatureService.count(temperatureClass)
}
