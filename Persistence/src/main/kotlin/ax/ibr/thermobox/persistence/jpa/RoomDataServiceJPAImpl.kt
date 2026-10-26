package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.persistence.dataservices.RoomDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class RoomDataServiceJPAImpl(emf: EntityManagerFactory,
                              entityClass: Class<Room>
) : RoomDataService, CrudJpaService<Room>(emf, entityClass) {

    override fun getAllByType(c: Room): List<Room> = read { em ->
        em.createQuery(
            "SELECT s FROM Room s WHERE TYPE(s) = :type",
            Room::class.java
        ).setParameter("type", c.javaClass)
            .resultList
    }
    override fun getByName(name: String): Room? = read { em ->
        em.createQuery(
            "SELECT s FROM Room s WHERE s.name = :name",
            Room::class.java
        ).setParameter("name", name)
            .resultList
            .firstOrNull()
    }

}
