package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.sql.Date
import java.time.LocalDateTime

@Entity
@DiscriminatorValue("Mesure")
class Mesurer : Temperature {
    constructor(value: Float, date: LocalDateTime) : super(value, date)
}