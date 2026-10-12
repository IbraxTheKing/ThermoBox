package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/** A salle a user pinned on their dashboard. */
@Entity
@Table(
    name = "favori",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "salle_id"])]
)
class Favori(
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
