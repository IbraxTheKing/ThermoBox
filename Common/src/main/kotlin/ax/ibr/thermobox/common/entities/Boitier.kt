package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

/**
 * A physical sensor box. Measures are attached to the box (through [SalleTempAttr]);
 * several boxes can sit in the same [Salle], at different spots.
 */
@Entity
@DiscriminatorValue("Boitier")
class Boitier(name: String?) : Salle(name) {
}
