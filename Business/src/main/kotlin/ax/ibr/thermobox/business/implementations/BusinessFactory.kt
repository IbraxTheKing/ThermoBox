package org.example.ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.utils.services.CrudService

class BusinessFactory {

    private lateinit var temperatureService: TemperatureService
    private lateinit var salleService: SalleService
    private lateinit var userService: UserService
    private lateinit var salleTempAttrService: SalleTempAttrService

    fun getTemperatureService(): TemperatureService {
        if (!::temperatureService.isInitialized) {
            temperatureService = TemperatureServiceImpl()
        }
        return temperatureService
    }

    fun getSalleService(): SalleService {
        if (!::salleService.isInitialized) {
            salleService = SalleServiceImpl()
        }
        return salleService
    }

    fun getUserService(): UserService {
        if (!::userService.isInitialized) {
            userService = UserServiceImpl()
        }
        return userService
    }

    fun getSalleTempAttrService(): SalleTempAttrService {
        if (!::salleTempAttrService.isInitialized) {
            salleTempAttrService = SalleTempAttrServiceImpl()
        }
        return salleTempAttrService
    }

}