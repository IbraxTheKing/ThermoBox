package ax.ibr.thermobox.common.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime


@Entity
@Table(name = "temperature")
class Temperature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name="value")
    final var value: Float? = null

    @Column(name="date")
    final var date: LocalDateTime? = null

    constructor(value: Float, date: LocalDateTime) {
        this.value = value
        this.date = date
    }

    constructor(value: Float) {
        this.value = value
        this.date = LocalDateTime.now()
    }

    constructor()


    override fun toString(): String {
        return "Temperature(id=$id, value=$value)"
    }
}