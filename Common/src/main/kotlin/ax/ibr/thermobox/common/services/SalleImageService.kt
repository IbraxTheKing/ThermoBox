package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.utils.services.CrudService
import java.time.LocalDateTime

interface SalleImageService : CrudService<SalleImage> {

    fun getBySalle(salle: Salle): SalleImage?

    /** Date of the image of every element that has one, by salle id, without loading the images. */
    fun getDates(): Map<Long, LocalDateTime>
}
