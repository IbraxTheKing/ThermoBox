package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.utils.services.CrudService
import java.time.LocalDateTime

interface RoomImageService : CrudService<RoomImage> {

    fun getByRoom(room: Room): RoomImage?

    /** Date of the image of every element that has one, by room id, without loading the images. */
    fun getDates(): Map<Long, LocalDateTime>
}
