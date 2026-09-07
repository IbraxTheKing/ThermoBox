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

    final var type: UserType? = null
        private set

    fun isPasswordValid(password: String): Boolean {
        return (Cryptographic(key = TODO()).encrypt(password)
                ==
                Cryptographic(key = TODO()).encrypt(this.password))
    }

    constructor(name: String, password: String) {
        this.username = name
        this.password = Cryptographic(
            key = TODO()
        ).encrypt(password)
        this.type = UserType.VIEWER
    }




}