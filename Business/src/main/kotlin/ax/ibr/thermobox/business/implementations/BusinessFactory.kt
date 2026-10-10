package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.io.mqtt.MqttDriver
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import ax.ibr.thermobox.common.services.SalleSalleAttrService

object BusinessFactory {

    private const val MQTT: Boolean = false

    private val temperatureServiceInstance: TemperatureService by lazy { TemperatureServiceImpl() }
    private val salleServiceInstance: SalleService by lazy { SalleServiceImpl() }
    private val userServiceInstance: UserService by lazy { UserServiceImpl() }
    private val salleTempAttrServiceInstance: SalleTempAttrService by lazy { SalleTempAttrServiceImpl() }
    private val salleSalleAttrServiceInstance: SalleSalleAttrService by lazy { SalleSalleAttrServiceImpl() }

    private val driverInstance: ProtocolDriver by lazy {
        if (MQTT) MqttDriver() else SimulatedProtocolDriver()
    }

    fun getTemperatureService(): TemperatureService = temperatureServiceInstance

    fun getSalleService(): SalleService = salleServiceInstance

    fun getUserService(): UserService = userServiceInstance

    fun getSalleTempAttrService(): SalleTempAttrService = salleTempAttrServiceInstance

    fun getSalleSalleAttrService(): SalleSalleAttrService = salleSalleAttrServiceInstance

    fun getDriver(): ProtocolDriver = driverInstance
}
