package ax.ibr.thermobox.business.mqtt

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle

class MqttSendReceiver : ProtocolDriver {

    override fun listen(): Void {
        TODO("Not yet implemented")
    }

    override fun listenToRooms(): Void {
        TODO("Not yet implemented")
    }

    override fun listenToTemperatures(): Void {
        TODO("Not yet implemented")
    }

    override fun sendConsigne(
        s: Salle,
        c: Consigne
    ): Void {
        TODO("Not yet implemented")
    }

}