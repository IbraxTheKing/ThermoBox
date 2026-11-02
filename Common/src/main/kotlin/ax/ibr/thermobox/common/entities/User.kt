package ax.ibr.thermobox.common.entities

import jakarta.persistence.*
import java.time.LocalDateTime

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

    /** Given name, family name and address of the person (an account imported from a list of staff). */
    @Column(name = "first_name")
    var firstName: String? = null

    @Column(name = "last_name")
    var lastName: String? = null

    @Column(name = "email")
    var email: String? = null

    /**
     * The password is a provisional one (account imported, or created at the first start): its
     * owner must choose their own before anything else (SecurityFilter lets only the account
     * endpoints through until then).
     */
    @Column(name = "password_change_required")
    var passwordChangeRequired: Boolean = false

    /** End of validity of the provisional password; null: no limit (or not provisional). */
    @Column(name = "provisional_expires_at")
    var provisionalExpiresAt: LocalDateTime? = null

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