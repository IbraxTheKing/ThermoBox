package ax.ibr.thermobox.common.entities

import ax.ibr.utils.Cryptographic
import jakarta.persistence.*

@Entity
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var username: String? = null

    private var password: String? = null
        set(password) {
            field = password
        }
    final var type: UserType? = null
        private set

    fun isPasswordValid(password: String): Boolean {
        return !(password.isEmpty() || this.password == password)
        /// return (Cryptographic(key = TODO()).encrypt(password)
        ///        ==
        ///        Cryptographic(key = TODO()).decrypt(this.password))
    }

    constructor(name: String, password: String) {
        this.username = name
        this.password = password // TODO: ENCRYPT IT!!!!
        this.type = UserType.VIEWER
    }

}