package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import java.time.Instant
import java.time.LocalDateTime

// The data service is a constructor parameter so tests can pass a mock.
class RoomTempAttrServiceImpl(
    private val roomTempAttrService: RoomTempAttrService = PersistenceFactory.getRoomTempAttrDataService()
) : RoomTempAttrService, Serializable {

    override fun getByRoom(roomId: Long): List<RoomTempAttr>? {
        return roomTempAttrService.getByRoom(roomId)
    }

    override fun getByRoom(room: Room): List<RoomTempAttr>? {
        return roomTempAttrService.getByRoom(room)
    }

    override fun getByTemperature(temperature: Temperature): RoomTempAttr? {
        return roomTempAttrService.getByTemperature(temperature)
    }

    override fun getByTemperature(temperatureId: Long): RoomTempAttr? {
        return roomTempAttrService.getByTemperature(temperatureId)
    }

    override fun getCurrentTemperatureFromRoom(
        room: Room,
        temperatureClass: Temperature
    ): Temperature? {
        return roomTempAttrService.getCurrentTemperatureFromRoom(room, temperatureClass)
    }

    override fun getCurrentTemperaturesFromRoom(room: Room): List<Temperature>? {
        return roomTempAttrService.getCurrentTemperaturesFromRoom(room)
    }

    override fun getSetpointFromRoom(room: Room): Setpoint? {
        return roomTempAttrService.getSetpointFromRoom(room)
    }

    override fun getTemperaturesFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        return roomTempAttrService.getTemperaturesFromRoomTimed(
            room,
            start,
            end
        )
    }

    override fun getSetpointsFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Setpoint> {
        return roomTempAttrService.getSetpointsFromRoomTimed(
            room,
            start,
            end
        )
    }

    override fun getAverageTemperatureFromRoom(
        room: Room,
        temperatureClass: Temperature
    ): Temperature? {
        return roomTempAttrService.getAverageTemperatureFromRoom(room, temperatureClass)
    }

    override fun getAverageTemperatureFromRoomTimed(
        room: Room,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        return roomTempAttrService.getAverageTemperatureFromRoomTimed(
            room,
            start,
            end
        )
    }

    override fun getAllByTime(
        start: Instant,
        end: Instant
    ): List<RoomTempAttr> {
        return roomTempAttrService.getAllByTime(start, end)
    }

    override fun add(t: RoomTempAttr) {
        roomTempAttrService.add(t)
    }

    override fun update(t: RoomTempAttr) {
        roomTempAttrService.update(t)
    }

    override fun remove(t: RoomTempAttr) {
        roomTempAttrService.remove(t)
    }

    override fun getAll(): List<RoomTempAttr> {
        return roomTempAttrService.getAll()
    }

    override fun getById(id: Long): RoomTempAttr? {
        return roomTempAttrService.getById(id)
    }

    override fun getLatestMeasurements(devices: Collection<Room>): Map<Long, Temperature> =
        roomTempAttrService.getLatestMeasurements(devices)

    override fun getLatestSetpoints(elements: Collection<Room>): Map<Long, Setpoint> =
        roomTempAttrService.getLatestSetpoints(elements)

    override fun getRoomIdsOfTemperatures(temperatureIds: Collection<Long>): Map<Long, Long> =
        roomTempAttrService.getRoomIdsOfTemperatures(temperatureIds)

    override fun getPage(offset: Int, limit: Int): List<RoomTempAttr> = roomTempAttrService.getPage(offset, limit)

    override fun getMeasurementsOf(devices: Collection<Room>, start: Instant, end: Instant): Map<Long, List<Temperature>> =
        roomTempAttrService.getMeasurementsOf(devices, start, end)

    override fun getHourlyMeasurementsOf(devices: Collection<Room>, start: Instant, end: Instant): Map<Long, List<Temperature>> =
        roomTempAttrService.getHourlyMeasurementsOf(devices, start, end)
}
