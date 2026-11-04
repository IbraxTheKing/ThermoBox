package ax.ibr.thermobox.business.io.mqtt

import ax.ibr.thermobox.business.hierarchy.HierarchyCache
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.rules.SetpointRules
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.getValue

/**
 * The boxes over MQTT: their announcements and measurements in, the setpoints out.
 *
 * Two connections to the broker. Paho reads a connection on one thread and hands what arrives
 * to the listeners one at a time, through a queue of 10 messages: while it is full, it stops
 * reading. With one connection for everything, a busy site (thousands of measurements a minute,
 * each one written to the database) kept that queue full, so the PUBACK of a setpoint was read
 * only once the measurements before it were saved: setpoints took up to a minute, the request of
 * the client waiting meanwhile. Now:
 * - [subscriber] only receives; its listener writes nothing itself: the measurements go to
 *   [writers], a few threads, so Paho keeps reading and several are saved at once;
 * - [publisher] only sends the setpoints, and waits [PUBLISH_TIMEOUT_MS] at most for the broker.
 */
class MqttDriver(
    private val brokerUrl: String = "tcp://localhost:1883",
    private val clientId: String = "thermobox-${System.currentTimeMillis()}",
    private val brokerUser: String? = null,
    private val brokerPassword: String? = null,
) : ProtocolDriver() {

    private fun connected(id: String) = MqttClient(brokerUrl, id, MemoryPersistence()).apply {
        connect(MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            brokerUser?.let { userName = it }
            brokerPassword?.let { password = it.toCharArray() }
        })
    }

    /** Receives the announcements and the measurements. */
    private val subscriber: MqttClient by lazy { connected(clientId) }

    /** Sends the setpoints: never behind the measurements received. */
    private val publisher: MqttClient by lazy { connected("$clientId-out").apply { timeToWait = PUBLISH_TIMEOUT_MS } }

    /**
     * Saves the measurements received. Past [QUEUE] waiting, the listener saves the next one
     * itself: Paho then reads more slowly, the broker keeps the rest (backpressure, nothing lost).
     */
    private val writers: ThreadPoolExecutor by lazy {
        val n = AtomicInteger()
        ThreadPoolExecutor(WRITERS, WRITERS, 60, TimeUnit.SECONDS, ArrayBlockingQueue(QUEUE), { r ->
            Thread(r, "thermobox-mqtt-writer-${n.incrementAndGet()}").apply { isDaemon = true }
        }, ThreadPoolExecutor.CallerRunsPolicy())
    }

    companion object {
        /** Measurements saved at once (each one takes a connection of the pool for a moment). */
        private const val WRITERS = 4
        private const val QUEUE = 10_000
        /** A setpoint not acknowledged by the broker by then: the request ends (the setpoint is saved). */
        private const val PUBLISH_TIMEOUT_MS = 10_000L

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
            subscriber.subscribe(topic) { _, message ->
                registerDevice(String(message.payload).trim())
            }
        }
    }

    override fun listenToTemperatures() {
        subscriber.subscribe(TOPIC_TEMPERATURES) { topic, message ->
            val roomId = topic.substringAfterLast("/").toLongOrNull() ?: return@subscribe
            val value = String(message.payload).trim().toFloatOrNull() ?: return@subscribe
            // Outside the limits chosen by an ADMIN: a sensor error, not a temperature
            if (!SetpointRules.acceptsMeasurement(value)) return@subscribe
            // The time it arrived, not the time it is saved
            val at = LocalDateTime.now()
            writers.execute { runCatching { save(roomId, value, at) }.onFailure { println("[MqttDriver] measurement of $roomId not saved: ${it.message}") } }
        }
    }

    private fun save(roomId: Long, value: Float, at: LocalDateTime) {
        // Only devices measure; anything else (room, floor…) is ignored. The hierarchy in memory
        // first (no query per measurement), the database for a box it does not know yet.
        val cached = BusinessFactory.getHierarchyCache().tree().elements[roomId]
        val room = (cached?.let { HierarchyCache.copy(it) } ?: roomService.getById(roomId)) as? Device ?: return
        // The link cascades to the temperature: saving it separately would insert it twice.
        roomTempAttrService.add(RoomTempAttr(room, Measurement(value, at)))
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
        publisher.publish(setpointTopic(roomId), message)
        publisher.publish(legacySetpointTopic(roomId), message)
    }

}