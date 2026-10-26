package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.services.CrudService

interface TemperatureService : CrudService<Temperature> {

    fun getByType(temperatureClass: Temperature) : List<Temperature>?

    fun getAverage(temperatureClass: Temperature) : Temperature?

    /**
     * At most [limit] temperatures of exactly [temperatureClass]'s kind (any kind when null), the
     * latest first, skipping [offset]: a page of a list that can hold millions of rows.
     */
    fun getPage(temperatureClass: Temperature?, offset: Int, limit: Int): List<Temperature> =
        (if (temperatureClass == null) getAll() else getByType(temperatureClass).orEmpty())
            .sortedByDescending { it.date }.drop(offset).take(limit)

    /** How many temperatures of [temperatureClass]'s kind (any kind when null) there are. */
    fun count(temperatureClass: Temperature?): Long =
        (if (temperatureClass == null) getAll() else getByType(temperatureClass).orEmpty()).size.toLong()
}