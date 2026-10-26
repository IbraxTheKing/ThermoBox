package ax.ibr.thermobox.common.entities

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity

/**
 * A physical sensor box. Measures are attached to the box (through [RoomTempAttr]);
 * several boxes can sit in the same [Room], at different spots.
 */
@Entity
@DiscriminatorValue("Device")
class Device(name: String?) : Room(name) {
}
