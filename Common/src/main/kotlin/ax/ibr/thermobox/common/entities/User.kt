package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

@Entity
class User {

    private var name: String

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var username: String? = null

    private var password: String? = null

    final var type: UserType? = null
        private set

    fun isPasswordValid(password: String): Boolean {
        return TODO("Provide the return value")
    }

    constructor(name: String, password: String) {
        this.name = name
        this.password = password
        this.type = UserType.VIEWER
    }




}