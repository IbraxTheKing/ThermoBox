package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

@Entity
@DiscriminatorValue("Etage")
class Etage(name: String?) : Salle(name) {
}