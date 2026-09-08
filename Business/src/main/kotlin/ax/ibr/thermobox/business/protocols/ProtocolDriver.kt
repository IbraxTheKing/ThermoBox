package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle

interface ProtocolDriver {
    fun listen(): Void

    fun listenToRooms(): Void

    fun listenToTemperatures(): Void

    fun sendConsigne(s: Salle, c: Consigne): Void
}