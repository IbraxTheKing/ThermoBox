package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.services.CrudService

interface TemperatureService : CrudService<Temperature> {

    fun getByType(temperatureClass: Temperature) : List<Temperature>?

    fun getAverage(temperatureClass: Temperature) : Temperature?
}