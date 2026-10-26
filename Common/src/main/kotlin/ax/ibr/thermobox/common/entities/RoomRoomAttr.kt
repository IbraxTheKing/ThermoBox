package ax.ibr.thermobox.common.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Containment link between two [Room]s: [roomA] (the parent) contains [roomB] (the child).
 *
 * Allowed links: Building → Floor, Floor or Building → Room, Room → Device; a child has a
 * single parent. These rules are enforced by the business layer.
 */
@Entity
@Table(name = "room_attr")
class RoomRoomAttr(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roomA_id", nullable = false)
    var roomA: Room,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roomB_id", nullable = false)
    var roomB: Room
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "${roomA.id} <-> ${roomB.id}"
    }
}