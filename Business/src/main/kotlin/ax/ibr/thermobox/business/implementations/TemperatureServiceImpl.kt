package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

// The data service is a constructor parameter so tests can pass a mock.
class TemperatureServiceImpl(
    private val temperatureService: TemperatureService = PersistenceFactory.getTemperatureDataService()
) : TemperatureService, Serializable {

    private val isFahrenheit: Boolean = false

    override fun getByType(temperatureClass: Temperature): List<Temperature>? {
        return temperatureService.getByType(temperatureClass)
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        return temperatureService.getAverage(temperatureClass)
    }

    override fun add(t: Temperature) {
        t.value?.let {
            if (isFahrenheit) {
                // TODO: check temperatures given in Fahrenheit too.
            }
            else {
                if (it > 50f) {
                    if (it > 100f){
                        throw ImpossibleValueException("Impossible value, or the house is on fire (${it} °C)")
                    }
                    throw TooHotException("The value is far too high [${it} °C]")
                }

                if (it < -10f) {
                    throw ImpossibleValueException("Impossible value, or this is the ice age (${it} °C)")
                }
            }

        }
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

}