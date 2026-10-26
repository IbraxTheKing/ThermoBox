package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

@Entity
@DiscriminatorValue("Floor")
class Floor(name: String?) : Room(name) {
}