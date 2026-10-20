package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/**
 * Marks a user whose access is limited to their [Acces] rows. A user without this row sees
 * everything: accounts that existed before access management keep what they had. A separate
 * table rather than a column of "user", so an existing database needs no migration (the
 * schema generation creates missing tables, it doesn't alter existing ones).
 */
@Entity
@Table(name = "acces_restreint")
class AccesRestreint(
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
