package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.persistence.dataservices.SalleImageDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDateTime

class SalleImageDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<SalleImage>
) : SalleImageDataService, CrudJpaService<SalleImage>(emf, entityClass) {

    override fun getBySalle(salle: Salle): SalleImage? {
        val id = salle.id ?: return null
        return read { em ->
            em.createQuery("SELECT i FROM SalleImage i WHERE i.salle.id = :id", SalleImage::class.java)
                .setParameter("id", id)
                .resultList
                .firstOrNull()
        }
    }

    override fun getDates(): Map<Long, LocalDateTime> = read { em ->
        em.createQuery("SELECT i.salle.id, i.updated FROM SalleImage i", Array<Any>::class.java)
            .resultList
            .associate { (it[0] as Number).toLong() to it[1] as LocalDateTime }
    }
}
