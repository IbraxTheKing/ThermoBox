package ax.ibr.thermobox.business.mqtt

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.services.RoomService
import ax.ibr.thermobox.common.services.RoomTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.utils.exceptions.NullException
import io.mockk.*
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * NOTE: the exact properties of Room/Temperature/Setpoint (other than
 * `id` / `value`, already visible in MqttDriver.kt) are not known
 * here -> the entities are mocked rather than built, and the
 * assertions check the interactions rather than the content of the
 * objects.
 */
/*
class MqttSendReceiverTest {

    private lateinit var roomService: RoomService
    private lateinit var temperatureService: TemperatureService
    private lateinit var roomTempAttrService: RoomTempAttrService
    private lateinit var receiver: MqttSendReceiver

    private val subscribedListeners = mutableMapOf<String, IMqttMessageListener>()

    @BeforeEach
    fun setUp() {
        roomService = mockk()
        temperatureService = mockk()
        roomTempAttrService = mockk()
        subscribedListeners.clear()

        mockkConstructor(MqttClient::class)
        every { anyConstructed<MqttClient>().connect(any<MqttConnectOptions>()) } just Runs
        every { anyConstructed<MqttClient>().subscribe(any<String>(), any<IMqttMessageListener>()) } answers {
            subscribedListeners[firstArg()] = secondArg()
        }
        every { anyConstructed<MqttClient>().publish(any<String>(), any<MqttMessage>()) } just Runs

        receiver = MqttSendReceiver(roomService, temperatureService, roomTempAttrService)
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(MqttClient::class)
    }

    // --- listenToRooms ---

    @Test
    fun `listenToRooms creates the room if it does not exist yet`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        every { roomService.getByName("Room101") } returns null
        every { roomService.update(any()) } just Runs

        listener.messageArrived("thermobox/rooms", MqttMessage("Room101".toByteArray()))

        verify(exactly = 1) { roomService.update(any()) }
    }

    @Test
    fun `listenToRooms does not create an existing room again`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        every { roomService.getByName("Room101") } returns mockk<Room>()

        listener.messageArrived("thermobox/rooms", MqttMessage("Room101".toByteArray()))

        verify(exactly = 0) { roomService.update(any()) }
    }

    @Test
    fun `listenToRooms ignores an empty message`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/rooms", MqttMessage("   ".toByteArray()))

        verify(exactly = 0) { roomService.getByName(any()) }
        verify(exactly = 0) { roomService.update(any()) }
    }

    // --- listenToTemperatures ---

    @Test
    fun `listenToTemperatures updates temperature and roomTempAttr for a valid message`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        every { roomService.getById(42L) } returns mockk<Room>()
        every { temperatureService.update(any()) } just Runs
        every { roomTempAttrService.update(any()) } just Runs

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("21.5".toByteArray()))

        verify(exactly = 1) { temperatureService.update(any()) }
        verify(exactly = 1) { roomTempAttrService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignores a non-numeric room id`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/temperatures/abc", MqttMessage("21.5".toByteArray()))

        verify(exactly = 0) { roomService.getById(any()) }
        verify(exactly = 0) { temperatureService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignores a non-numeric value`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("hot".toByteArray()))

        verify(exactly = 0) { temperatureService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignores a message when the room is not found`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        every { roomService.getById(42L) } returns null

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("21.5".toByteArray()))

        verify(exactly = 0) { temperatureService.update(any()) }
        verify(exactly = 0) { roomTempAttrService.update(any()) }
    }

    // --- sendSetpoint ---

    @Test
    fun `sendSetpoint throws a NullException when the room has no id`() {
        val room = mockk<Room>()
        every { room.id } returns null
        val setpoint = mockk<Setpoint>()

        assertThrows(NullException::class.java) {
            receiver.sendSetpoint(room, setpoint)
        }
    }

    @Test
    fun `sendSetpoint publishes nothing when the setpoint has no value`() {
        val room = mockk<Room>()
        every { room.id } returns 7L
        val setpoint = mockk<Setpoint>()
        every { setpoint.value } returns null

        receiver.sendSetpoint(room, setpoint)

        verify(exactly = 0) { anyConstructed<MqttClient>().publish(any(), any()) }
    }

    @Test
    fun `sendSetpoint publishes on the right topic with the setpoint value`() {
        val room = mockk<Room>()
        every { room.id } returns 7L
        val setpoint = mockk<Setpoint>()
        every { setpoint.value } returns 21.5F

        val topicSlot = slot<String>()
        val messageSlot = slot<MqttMessage>()
        every { anyConstructed<MqttClient>().publish(capture(topicSlot), capture(messageSlot)) } just Runs

        receiver.sendSetpoint(room, setpoint)

        assertEquals("thermobox/setpoints/7", topicSlot.captured)
        assertEquals("21.5", String(messageSlot.captured.payload))
        assertEquals(1, messageSlot.captured.qos)
        assertEquals(true, messageSlot.captured.isRetained)
    }
}
*/