package org.example.ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class TemperatureServiceImpl : TemperatureService {

    private val temperatureService = PersistenceFactory().getTemperatureDataService()

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