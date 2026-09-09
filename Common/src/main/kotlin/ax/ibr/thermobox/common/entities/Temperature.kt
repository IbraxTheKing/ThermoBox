package ax.ibr.thermobox.common.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.sql.Date


@Entity
class Temperature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    final var value: Float? = null

    final var date: Date? = null

    constructor(value: Float, date: Date) {
        this.value = value
        this.date = date
    }

    constructor()


}