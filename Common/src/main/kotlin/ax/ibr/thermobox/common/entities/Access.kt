package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/**
 * Access of a user to a building, a floor or a room, and to everything it contains (see
 * AccessService). Only read for users whose access is limited ([RestrictedAccess]).
 */
@Entity
@Table(
    name = "user_access",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "room_id"])]
)
class Access(
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
