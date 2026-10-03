package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.business.io.mqtt.MqttDriver
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver

class BusinessFactory {

    private lateinit var temperatureService: TemperatureService
    private lateinit var salleService: SalleService
    private lateinit var userService: UserService
    private lateinit var salleTempAttrService: SalleTempAttrService


    private lateinit var driver: ProtocolDriver
    private val MQTT: Boolean = false

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

    // Boxes stuffs

    fun sendConsigne(s: Salle, c: Consigne) {
        initializeDriver()
        driver.sendConsigne(s,c)
    }

    fun listenToBoxes() {
        initializeDriver()
        driver.listen()
    }

    fun getDriver(): ProtocolDriver {
        initializeDriver()
        return driver
    }

    private fun initializeDriver() {
        if (!::driver.isInitialized) {
            if (MQTT) {
                driver = MqttDriver()
            }
            // .... //
            driver = SimulatedProtocolDriver()
        }
        return
    }

}