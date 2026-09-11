package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.sql.Date

@Entity
@DiscriminatorValue("Consigne")
class Consigne : Temperature {
    constructor(value: Float, date: Date) : super(value, date)
}