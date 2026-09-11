package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr

interface ProtocolDriver {
    fun listen()

    fun listenToRooms()

    fun listenToTemperatures()

    fun sendConsigne(s: Salle, c: Consigne)
}