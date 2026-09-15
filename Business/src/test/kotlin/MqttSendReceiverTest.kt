package ax.ibr.thermobox.business.mqtt

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
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
 * NOTE: les propriétés exactes de Salle/Temperature/Consigne (autres que
 * `id` / `value`, déjà visibles dans MqttDriver.kt) ne sont pas
 * connues ici -> les entités sont mockées plutôt que construites, et les
 * assertions portent sur les interactions plutôt que sur le contenu des
 * objets.
 */
/*
class MqttSendReceiverTest {

    private lateinit var salleService: SalleService
    private lateinit var temperatureService: TemperatureService
    private lateinit var salleTempAttrService: SalleTempAttrService
    private lateinit var receiver: MqttSendReceiver

    private val subscribedListeners = mutableMapOf<String, IMqttMessageListener>()

    @BeforeEach
    fun setUp() {
        salleService = mockk()
        temperatureService = mockk()
        salleTempAttrService = mockk()
        subscribedListeners.clear()

        mockkConstructor(MqttClient::class)
        every { anyConstructed<MqttClient>().connect(any<MqttConnectOptions>()) } just Runs
        every { anyConstructed<MqttClient>().subscribe(any<String>(), any<IMqttMessageListener>()) } answers {
            subscribedListeners[firstArg()] = secondArg()
        }
        every { anyConstructed<MqttClient>().publish(any<String>(), any<MqttMessage>()) } just Runs

        receiver = MqttSendReceiver(salleService, temperatureService, salleTempAttrService)
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(MqttClient::class)
    }

    // --- listenToRooms ---

    @Test
    fun `listenToRooms cree la salle si elle n'existe pas encore`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        every { salleService.getByName("Salle101") } returns null
        every { salleService.update(any()) } just Runs

        listener.messageArrived("thermobox/salles", MqttMessage("Salle101".toByteArray()))

        verify(exactly = 1) { salleService.update(any()) }
    }

    @Test
    fun `listenToRooms ne recree pas une salle deja existante`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        every { salleService.getByName("Salle101") } returns mockk<Salle>()

        listener.messageArrived("thermobox/salles", MqttMessage("Salle101".toByteArray()))

        verify(exactly = 0) { salleService.update(any()) }
    }

    @Test
    fun `listenToRooms ignore un message vide`() {
        receiver.listenToRooms()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/salles", MqttMessage("   ".toByteArray()))

        verify(exactly = 0) { salleService.getByName(any()) }
        verify(exactly = 0) { salleService.update(any()) }
    }

    // --- listenToTemperatures ---

    @Test
    fun `listenToTemperatures met a jour temperature et salleTempAttr pour un message valide`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        every { salleService.getById(42L) } returns mockk<Salle>()
        every { temperatureService.update(any()) } just Runs
        every { salleTempAttrService.update(any()) } just Runs

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("21.5".toByteArray()))

        verify(exactly = 1) { temperatureService.update(any()) }
        verify(exactly = 1) { salleTempAttrService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignore un id de salle non numerique`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/temperatures/abc", MqttMessage("21.5".toByteArray()))

        verify(exactly = 0) { salleService.getById(any()) }
        verify(exactly = 0) { temperatureService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignore une valeur non numerique`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("chaud".toByteArray()))

        verify(exactly = 0) { temperatureService.update(any()) }
    }

    @Test
    fun `listenToTemperatures ignore un message si la salle est introuvable`() {
        receiver.listenToTemperatures()
        val listener = subscribedListeners.values.single()

        every { salleService.getById(42L) } returns null

        listener.messageArrived("thermobox/temperatures/42", MqttMessage("21.5".toByteArray()))

        verify(exactly = 0) { temperatureService.update(any()) }
        verify(exactly = 0) { salleTempAttrService.update(any()) }
    }

    // --- sendConsigne ---

    @Test
    fun `sendConsigne leve une NullException si la salle n'a pas d'id`() {
        val salle = mockk<Salle>()
        every { salle.id } returns null
        val consigne = mockk<Consigne>()

        assertThrows(NullException::class.java) {
            receiver.sendConsigne(salle, consigne)
        }
    }

    @Test
    fun `sendConsigne ne publie rien si la consigne n'a pas de valeur`() {
        val salle = mockk<Salle>()
        every { salle.id } returns 7L
        val consigne = mockk<Consigne>()
        every { consigne.value } returns null

        receiver.sendConsigne(salle, consigne)

        verify(exactly = 0) { anyConstructed<MqttClient>().publish(any(), any()) }
    }

    @Test
    fun `sendConsigne publie sur le bon topic avec la valeur de la consigne`() {
        val salle = mockk<Salle>()
        every { salle.id } returns 7L
        val consigne = mockk<Consigne>()
        every { consigne.value } returns 21.5F

        val topicSlot = slot<String>()
        val messageSlot = slot<MqttMessage>()
        every { anyConstructed<MqttClient>().publish(capture(topicSlot), capture(messageSlot)) } just Runs

        receiver.sendConsigne(salle, consigne)

        assertEquals("thermobox/consignes/7", topicSlot.captured)
        assertEquals("21.5", String(messageSlot.captured.payload))
        assertEquals(1, messageSlot.captured.qos)
        assertEquals(true, messageSlot.captured.isRetained)
    }
}
*/