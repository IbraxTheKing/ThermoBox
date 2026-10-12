package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

@Entity
@Table(name = "user")
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name="username")
    var username: String? = null

    /** BCrypt hash once saved: UserService hashes any clear-text value on add/update. */
    @Column(name="password")
    var password: String? = null
        set(password) {
            field = password
        }

    @Column(name="type")
    var type: UserType? = null

    constructor(name: String, password: String) {
        this.username = name
        this.password = password
        this.type = UserType.VIEWER
    }

    constructor(name: String, password: String, type: UserType) {
        this.username = name
        this.password = password
        this.type = type
    }

    constructor()

}