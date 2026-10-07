package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.io.mqtt.MqttDriver
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver

object BusinessFactory {

    private const val MQTT: Boolean = false

    private val temperatureService: TemperatureService by lazy { TemperatureServiceImpl() }
    private val salleService: SalleService by lazy { SalleServiceImpl() }
    private val userService: UserService by lazy { UserServiceImpl() }
    private val salleTempAttrService: SalleTempAttrService by lazy { SalleTempAttrServiceImpl() }

    private val driver: ProtocolDriver by lazy {
        if (MQTT) MqttDriver() else SimulatedProtocolDriver()
    }

    fun getTemperatureService(): TemperatureService = temperatureService

    fun getSalleService(): SalleService = salleService

    fun getUserService(): UserService = userService

    fun getSalleTempAttrService(): SalleTempAttrService = salleTempAttrService

    fun getDriver(): ProtocolDriver = driver
}
