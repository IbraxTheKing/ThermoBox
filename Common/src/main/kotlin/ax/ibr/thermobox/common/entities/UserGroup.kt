package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

/**
 * Group of users (ADMIN): every member gets the group's accesses (buildings, floors, rooms and
 * what they contain), on top of their own. Only matters for users whose access is limited.
 */
@Entity
@Table(name = "user_group")
class UserGroup(
    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String,

    @Column(name = "description")
    var description: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

/** A user in a [UserGroup]. */
@Entity
@Table(name = "user_group_member", uniqueConstraints = [UniqueConstraint(columnNames = ["user_group_id", "user_id"])])
class UserGroupMember(
    @ManyToOne
    @JoinColumn(name = "user_group_id", nullable = false)
    var userGroup: UserGroup,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

/** An element a [UserGroup] gives access to, with everything it contains. */
@Entity
@Table(name = "user_group_access", uniqueConstraints = [UniqueConstraint(columnNames = ["user_group_id", "room_id"])])
class UserGroupAccess(
    @ManyToOne
    @JoinColumn(name = "user_group_id", nullable = false)
    var userGroup: UserGroup,

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    var room: Room,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
