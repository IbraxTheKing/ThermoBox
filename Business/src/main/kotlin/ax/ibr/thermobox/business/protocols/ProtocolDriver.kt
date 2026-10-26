package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.rules.SetpointRules
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.utils.exceptions.DontExistException
import ax.ibr.utils.exceptions.NullException

abstract class ProtocolDriver {

    protected val roomService: RoomService = BusinessFactory.getRoomService()
    protected val temperatureService: TemperatureService = BusinessFactory.getTemperatureService()
    protected val roomTempAttrService: RoomTempAttrService = BusinessFactory.getRoomTempAttrService()

    abstract fun listen()

    abstract fun listenToRooms()

    abstract fun listenToTemperatures()

    open fun sendSetpoint(s: Room, c: Setpoint) {
        checkSetpoint(s, c)
    }

    /** Boxes known in database (measures only ever go to a box). */
    protected fun getDevices(): List<Device> =
        roomService.getAllByType(Device(null)).filterIsInstance<Device>()

    /** Saves a box that announced itself, unless a box with that name already exists. */
    protected fun registerDevice(name: String): Boolean {
        if (name.isBlank() || getDevices().any { it.name == name }) return false
        roomService.add(Device(name))
        return true
    }

    private fun checkSetpoint(s: Room, c: Setpoint) {
        if (s.id == null) {
            throw NullException("Room id is null")
        }
        if (s.id != roomService.getById(s.id!!)?.id) {
            throw DontExistException("Room (id=${s.id}) doesn't exist in database.")
        }
        SetpointRules.check(c.value!!)
    }

}
