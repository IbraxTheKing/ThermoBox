package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.utils.services.CrudService

interface RoomService : CrudService<Room> {

    fun getAllByType(c: Room): List<Room>

    fun getByName(name: String): Room?
}