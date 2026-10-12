package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.images.ImageProcessor
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.common.services.SalleImageService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import java.time.LocalDateTime

class SalleImageServiceImpl(
    private val images: SalleImageService = PersistenceFactory.getSalleImageDataService()
) : SalleImageService, Serializable {

    override fun getBySalle(salle: Salle): SalleImage? = images.getBySalle(salle)

    override fun getDates(): Map<Long, LocalDateTime> = images.getDates()

    /**
     * Stores the image of `t.salle`, replacing the one it had. The file is re-encoded first
     * (see [ImageProcessor]): only checked, reasonably sized JPEGs reach the database.
     *
     * @throws IllegalArgumentException if the file is not a readable image or is too big.
     */
    override fun add(t: SalleImage) {
        val data = ImageProcessor.toJpeg(t.data)
        val image = images.getBySalle(t.salle) ?: t
        image.data = data
        image.contentType = ImageProcessor.CONTENT_TYPE
        image.updated = LocalDateTime.now()
        if (image.id == null) images.add(image) else images.update(image)
    }

    /** Same as [add]. */
    override fun update(t: SalleImage) = add(t)

    override fun remove(t: SalleImage) = images.remove(t)

    override fun getAll(): List<SalleImage> = images.getAll()

    override fun getById(id: Long): SalleImage? = images.getById(id)
}
