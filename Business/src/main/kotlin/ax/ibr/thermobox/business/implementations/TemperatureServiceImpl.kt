package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class TemperatureServiceImpl : TemperatureService {

    private val temperatureService = PersistenceFactory().getTemperatureDataService()

    override fun add(t: Temperature) {
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