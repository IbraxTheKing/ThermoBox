package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.io.mqtt.MqttDriver
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.common.services.UserService
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import ax.ibr.thermobox.common.services.RoomRoomAttrService
import ax.ibr.thermobox.common.services.ClimateService
import ax.ibr.thermobox.common.services.FavoriteService
import ax.ibr.thermobox.common.services.HierarchyService
import ax.ibr.thermobox.common.services.RoomImageService
import ax.ibr.thermobox.common.services.AccessService
import ax.ibr.thermobox.common.services.UserGroupService
import ax.ibr.thermobox.business.update.UpdateServiceImpl
import ax.ibr.thermobox.business.config.ConfigDriver

object BusinessFactory {

    private val temperatureServiceInstance: TemperatureService by lazy { TemperatureServiceImpl() }
    private val roomServiceInstance: RoomService by lazy { RoomServiceImpl() }
    private val userServiceInstance: UserService by lazy { UserServiceImpl() }
    private val roomTempAttrServiceInstance: RoomTempAttrService by lazy { RoomTempAttrServiceImpl() }
    private val roomRoomAttrServiceInstance: RoomRoomAttrService by lazy { RoomRoomAttrServiceImpl() }
    private val hierarchyServiceInstance: HierarchyService by lazy { HierarchyServiceImpl() }
    private val climateServiceInstance: ClimateService by lazy { ClimateServiceImpl() }
    private val favoriteServiceInstance: FavoriteService by lazy { FavoriteServiceImpl() }
    private val roomImageServiceInstance: RoomImageService by lazy { RoomImageServiceImpl() }

    private val accessServiceInstance: AccessService by lazy { AccessServiceImpl() }

    private val updateServiceInstance: UpdateServiceImpl by lazy { UpdateServiceImpl() }

    private val driverInstance: ProtocolDriver by lazy {
        // THERMOBOX_DRIVER / THERMOBOX_MQTT_* (see ConfigDriver)
        val config = ConfigDriver.fromEnvironment()
        when (config.type) {
            ConfigDriver.DriverType.MQTT -> MqttDriver(config.mqttUrl, brokerUser = config.mqttUser, brokerPassword = config.mqttPassword)
            ConfigDriver.DriverType.SIMULATED -> SimulatedProtocolDriver()
        }
    }

    fun getTemperatureService(): TemperatureService = temperatureServiceInstance

    fun getRoomService(): RoomService = roomServiceInstance

    fun getUserService(): UserService = userServiceInstance

    fun getRoomTempAttrService(): RoomTempAttrService = roomTempAttrServiceInstance

    fun getRoomRoomAttrService(): RoomRoomAttrService = roomRoomAttrServiceInstance

    fun getHierarchyService(): HierarchyService = hierarchyServiceInstance

    fun getClimateService(): ClimateService = climateServiceInstance

    fun getFavoriteService(): FavoriteService = favoriteServiceInstance

    fun getRoomImageService(): RoomImageService = roomImageServiceInstance

    /** Implementation type: the servers also call start() / stop() on it. */
    fun getUpdateService(): UpdateServiceImpl = updateServiceInstance

    /** Who sees which buildings, floors and rooms. */
    fun getAccessService(): AccessService = accessServiceInstance

    private val userGroupServiceInstance: UserGroupService by lazy { UserGroupServiceImpl() }

    /** Groups of users and the accesses they give. */
    fun getUserGroupService(): UserGroupService = userGroupServiceInstance

    fun getDriver(): ProtocolDriver = driverInstance
}
