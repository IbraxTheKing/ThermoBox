package ax.ibr.thermobox.common.entities

import ax.ibr.thermobox.common.services.SalleService
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class SalleTempAttr(
    @Id
    var id: Long? = null,
    salle: Salle, temperature: Temperature) {}