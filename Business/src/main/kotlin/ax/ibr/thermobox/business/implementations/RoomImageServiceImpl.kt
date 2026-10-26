package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.images.ImageProcessor
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.common.services.RoomImageService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import java.time.LocalDateTime

class RoomImageServiceImpl(
    private val images: RoomImageService = PersistenceFactory.getRoomImageDataService()
) : RoomImageService, Serializable {

    override fun getByRoom(room: Room): RoomImage? = images.getByRoom(room)

    override fun getDates(): Map<Long, LocalDateTime> = images.getDates()

    /**
     * Stores the image of `t.room`, replacing the one it had. The file is re-encoded first
     * (see [ImageProcessor]): only checked, reasonably sized JPEGs reach the database.
     *
     * @throws IllegalArgumentException if the file is not a readable image or is too big.
     */
    override fun add(t: RoomImage) {
        val data = ImageProcessor.toJpeg(t.data)
        val image = images.getByRoom(t.room) ?: t
        image.data = data
        image.contentType = ImageProcessor.CONTENT_TYPE
        image.updated = LocalDateTime.now()
        if (image.id == null) images.add(image) else images.update(image)
    }

    /** Same as [add]. */
    override fun update(t: RoomImage) = add(t)

    override fun remove(t: RoomImage) = images.remove(t)

    override fun getAll(): List<RoomImage> = images.getAll()

    override fun getById(id: Long): RoomImage? = images.getById(id)
}
