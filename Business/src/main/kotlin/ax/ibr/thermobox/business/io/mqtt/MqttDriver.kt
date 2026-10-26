package ax.ibr.thermobox.business.io.mqtt

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.utils.exceptions.NullException
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.time.LocalDateTime
import kotlin.getValue

class MqttDriver(
    private val brokerUrl: String = "tcp://localhost:1883",
    private val clientId: String = "thermobox-${System.currentTimeMillis()}",
    private val brokerUser: String? = null,
    private val brokerPassword: String? = null,
) : ProtocolDriver() {

    private val client: MqttClient by lazy {
        MqttClient(brokerUrl, clientId, MemoryPersistence()).apply {
            connect(MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = true
                brokerUser?.let { userName = it }
                brokerPassword?.let { password = it.toCharArray() }
            })
        }
    }

    companion object {
        private const val TOPIC_ROOMS = "thermobox/rooms"
        private const val TOPIC_TEMPERATURES = "thermobox/temperatures/+"
        private fun setpointTopic(roomId: Long) = "thermobox/setpoints/$roomId"

        // Topics of the devices installed before 26.11, still served: their firmware announces
        // itself on "thermobox/salles" and reads its setpoint on "thermobox/consignes/<id>".
        private const val LEGACY_TOPIC_ROOMS = "thermobox/salles"
        private fun legacySetpointTopic(roomId: Long) = "thermobox/consignes/$roomId"
    }

    override fun listen() {
        listenToRooms()
        listenToTemperatures()
    }

    override fun listenToRooms() {
        for (topic in listOf(TOPIC_ROOMS, LEGACY_TOPIC_ROOMS)) {
            client.subscribe(topic) { _, message ->
                registerDevice(String(message.payload).trim())
            }
        }
    }

    override fun listenToTemperatures() {
        client.subscribe(TOPIC_TEMPERATURES) { topic, message ->
            val roomId = topic.substringAfterLast("/").toLongOrNull() ?: return@subscribe
            val value = String(message.payload).trim().toFloatOrNull() ?: return@subscribe

            // Only devices measure; anything else (room, floor…) is ignored.
            val room = roomService.getById(roomId) as? Device ?: return@subscribe
            val temperature = Measurement(value, LocalDateTime.now())
            // The link cascades to the temperature: saving it separately would insert it twice.
            roomTempAttrService.add(RoomTempAttr(room, temperature))
        }
    }

    override fun sendSetpoint(s: Room, c: Setpoint) {
        super.sendSetpoint(s, c)
        val roomId = requireNotNull(s?.id) {
            throw NullException("The room must have an id to receive a setpoint") }
        val payload = c.value?.toString() ?: return
        val message = MqttMessage(payload.toByteArray()).apply {
            qos = 1
            isRetained = true
        }
        client.publish(setpointTopic(roomId), message)
        client.publish(legacySetpointTopic(roomId), message)
    }

}