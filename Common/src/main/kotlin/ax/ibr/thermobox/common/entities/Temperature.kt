package ax.ibr.thermobox.common.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Date
import java.time.LocalDateTime


@Entity
class Temperature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    final var value: Float? = null

    final var date: LocalDateTime? = null

    constructor(value: Float, date: LocalDateTime) {
        this.value = value
        this.date = date
    }

    constructor()


    override fun toString(): String {
        return "Temperature(id=$id, value=$value)"
    }
}