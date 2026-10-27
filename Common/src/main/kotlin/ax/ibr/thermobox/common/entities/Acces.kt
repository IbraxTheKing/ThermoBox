package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/**
 * Access of a user to a bâtiment, an étage or a salle, and to everything it contains (see
 * AccesService). Only read for users whose access is limited ([AccesRestreint]).
 */
@Entity
@Table(
    name = "acces",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "salle_id"])]
)
class Acces(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne
    @JoinColumn(name = "salle_id", nullable = false)
    var salle: Salle
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
