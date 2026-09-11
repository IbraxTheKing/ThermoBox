package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.sql.Date

@Entity
@DiscriminatorValue("Mesure")
class Mesurer : Temperature {
    constructor(value: Float, date: Date) : super(value, date)
}