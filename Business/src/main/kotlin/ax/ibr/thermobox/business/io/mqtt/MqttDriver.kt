package ax.ibr.thermobox.business.io.mqtt

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.utils.exceptions.NullException
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.time.LocalDateTime
import kotlin.getValue

class MqttDriver(
    private val brokerUrl: String = "tcp://localhost:1883",
    private val clientId: String = "thermobox-${System.currentTimeMillis()}"
) : ProtocolDriver() {

    private val client: MqttClient by lazy {
        MqttClient(brokerUrl, clientId, MemoryPersistence()).apply {
            connect(MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = true
            })
        }
    }

    companion object {
        private const val TOPIC_SALLES = "thermobox/salles"
        private const val TOPIC_TEMPERATURES = "thermobox/temperatures/+"
        private fun consigneTopic(salleId: Long) = "thermobox/consignes/$salleId"
    }

    override fun listen() {
        listenToRooms()
        listenToTemperatures()
    }

    override fun listenToRooms() {
        client.subscribe(TOPIC_SALLES) { _, message ->
            registerBoitier(String(message.payload).trim())
        }
    }

    override fun listenToTemperatures() {
        client.subscribe(TOPIC_TEMPERATURES) { topic, message ->
            val salleId = topic.substringAfterLast("/").toLongOrNull() ?: return@subscribe
            val value = String(message.payload).trim().toFloatOrNull() ?: return@subscribe

            // Only boxes measure; anything else (salle, étage…) is ignored.
            val salle = salleService.getById(salleId) as? Boitier ?: return@subscribe
            val temperature = Mesurer(value, LocalDateTime.now())
            // The link cascades to the temperature: saving it separately would insert it twice.
            salleTempAttrService.add(SalleTempAttr(salle, temperature))
        }
    }

    override fun sendConsigne(s: Salle, c: Consigne) {
        super.sendConsigne(s, c)
        val salleId = requireNotNull(s?.id) {
            throw NullException("La salle doit avoir un id pour envoyer une consigne") }
        val payload = c.value?.toString() ?: return
        val message = MqttMessage(payload.toByteArray()).apply {
            qos = 1
            isRetained = true
        }
        client.publish(consigneTopic(salleId), message)
    }

}