package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Measurement
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

    /**
     * The latest measurement of each of [devices], by device id (devices that never measured are
     * left out). The storage answers it in one query; the default asks device by device.
     */
    fun getLatestMeasurements(devices: Collection<Room>): Map<Long, Temperature> =
        devices.mapNotNull { d -> d.id?.let { id -> getCurrentTemperatureFromRoom(d, Measurement(0f, LocalDateTime.now()))?.let { id to it } } }.toMap()

    /** The latest setpoint of each of [elements] (their own, not inherited), by element id. */
    fun getLatestSetpoints(elements: Collection<Room>): Map<Long, Setpoint> =
        elements.mapNotNull { e -> e.id?.let { id -> getSetpointFromRoom(e)?.let { id to it } } }.toMap()

    /** The element each of [temperatureIds] belongs to: temperature id → room id. */
    fun getRoomIdsOfTemperatures(temperatureIds: Collection<Long>): Map<Long, Long> =
        temperatureIds.mapNotNull { id -> getByTemperature(id)?.room?.id?.let { id to it } }.toMap()

    /** At most [limit] links, the most recent temperatures first, skipping [offset]. */
    fun getPage(offset: Int, limit: Int): List<RoomTempAttr> =
        getAll().sortedByDescending { it.temperature.date }.drop(offset).take(limit)
}