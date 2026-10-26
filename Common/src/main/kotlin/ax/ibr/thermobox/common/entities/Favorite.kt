package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/** A room a user pinned on their dashboard. */
@Entity
@Table(
    name = "favorite",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "room_id"])]
)
class Favorite(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    var room: Room
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
