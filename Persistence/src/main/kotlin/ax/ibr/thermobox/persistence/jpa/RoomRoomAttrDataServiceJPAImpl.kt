package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.persistence.dataservices.RoomRoomAttrDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManagerFactory

class RoomRoomAttrDataServiceJPAImpl(
    emf: EntityManagerFactory,
    entityClass: Class<RoomRoomAttr>
) : RoomRoomAttrDataService,
    CrudJpaService<RoomRoomAttr>(emf, entityClass) {

    override fun getByRoom(s: Room): List<RoomRoomAttr> = read { em ->
        em.createQuery(
            "SELECT ssa FROM RoomRoomAttr ssa WHERE ssa.roomA = :room OR ssa.roomB = :room",
            RoomRoomAttr::class.java
        ).setParameter("room", s)
            .resultList
    }

    override fun getChildren(parent: Room): List<Room> {
        val id = parent.id ?: return emptyList()
        return read { em ->
            em.createQuery(
                "SELECT ssa.roomB FROM RoomRoomAttr ssa WHERE ssa.roomA.id = :id",
                Room::class.java
            ).setParameter("id", id)
                .resultList
        }
    }

    override fun getParent(child: Room): Room? {
        val id = child.id ?: return null
        return read { em ->
            em.createQuery(
                "SELECT ssa.roomA FROM RoomRoomAttr ssa WHERE ssa.roomB.id = :id",
                Room::class.java
            ).setParameter("id", id)
                .setMaxResults(1)
                .resultList
                .firstOrNull()
        }
    }
}