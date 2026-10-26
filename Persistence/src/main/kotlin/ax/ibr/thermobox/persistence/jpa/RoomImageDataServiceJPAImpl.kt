package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.persistence.dataservices.RoomImageDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDateTime

class RoomImageDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<RoomImage>
) : RoomImageDataService, CrudJpaService<RoomImage>(emf, entityClass) {

    override fun getByRoom(room: Room): RoomImage? {
        val id = room.id ?: return null
        return read { em ->
            em.createQuery("SELECT i FROM RoomImage i WHERE i.room.id = :id", RoomImage::class.java)
                .setParameter("id", id)
                .resultList
                .firstOrNull()
        }
    }

    override fun getDates(): Map<Long, LocalDateTime> = read { em ->
        em.createQuery("SELECT i.room.id, i.updated FROM RoomImage i", Array<Any>::class.java)
            .resultList
            .associate { (it[0] as Number).toLong() to it[1] as LocalDateTime }
    }
}
