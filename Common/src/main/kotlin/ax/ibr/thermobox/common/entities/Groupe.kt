package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/**
 * Group of users (ADMIN): every member gets the group's accesses (bâtiments, étages, salles and
 * what they contain), on top of their own. Only matters for users whose access is limited.
 */
@Entity
@Table(name = "groupe")
class Groupe(
    @Column(name = "nom", nullable = false, unique = true, length = 100)
    var nom: String,

    @Column(name = "description")
    var description: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

/** A user in a [Groupe]. */
@Entity
@Table(name = "groupe_membre", uniqueConstraints = [UniqueConstraint(columnNames = ["groupe_id", "user_id"])])
class GroupeMembre(
    @ManyToOne
    @JoinColumn(name = "groupe_id", nullable = false)
    var groupe: Groupe,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

/** An element a [Groupe] gives access to, with everything it contains. */
@Entity
@Table(name = "groupe_acces", uniqueConstraints = [UniqueConstraint(columnNames = ["groupe_id", "salle_id"])])
class GroupeAcces(
    @ManyToOne
    @JoinColumn(name = "groupe_id", nullable = false)
    var groupe: Groupe,

    @ManyToOne
    @JoinColumn(name = "salle_id", nullable = false)
    var salle: Salle,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
