package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService

class TemperatureDataServiceJDBCImpl : TemperatureDataService {

    override fun getByType(temperatureClass: Temperature): List<Temperature>? {
        TODO("Not yet implemented")
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        TODO("Not yet implemented")
    }

    override fun add(t: Temperature) {
        TODO("Not yet implemented")
    }

    override fun update(t: Temperature) {
        TODO("Not yet implemented")
    }

    override fun remove(t: Temperature) {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<Temperature> {
        TODO("Not yet implemented")
    }

    override fun getById(id: Long): Temperature? {
        TODO("Not yet implemented")
    }
}