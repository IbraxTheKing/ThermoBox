package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.services.CrudService
import java.time.Instant
import java.time.LocalDateTime

interface RoomTempAttrService: CrudService<RoomTempAttr> {

    fun getByRoom(roomId: Long): List<RoomTempAttr>?
    fun getByRoom(room: Room): List<RoomTempAttr>?
    fun getByTemperature(temperature: Temperature): RoomTempAttr?
    fun getByTemperature(temperatureId: Long): RoomTempAttr?

    fun getCurrentTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature?
    fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? // Gets the current temperature + setpoint

    fun getSetpointFromRoom(room: Room): Setpoint?

    fun getTemperaturesFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature>
    fun getSetpointsFromRoomTimed(room: Room, start: Instant, end: Instant): List<Setpoint>

    fun getAverageTemperatureFromRoom(room: Room, temperatureClass: Temperature): Temperature?
    fun getAverageTemperatureFromRoomTimed(room: Room, start: Instant, end: Instant): List<Temperature> // First is Temp, second is Setpoint

    fun getAllByTime(start: Instant, end: Instant) : List<RoomTempAttr>
}