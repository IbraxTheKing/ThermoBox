package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

@Entity
@DiscriminatorValue("Batiment")
class Batiment(name: String?) : Salle(name) {
}