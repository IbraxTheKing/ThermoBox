package ax.ibr.thermobox.business.config

import ax.ibr.thermobox.business.config.ConfigDriver.TypeDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigDriverTest {

    @Test
    fun `simule par defaut`() {
        val c = ConfigDriver.depuisEnvironnement(emptyMap())
        assertEquals(TypeDriver.SIMULE, c.type)
        assertEquals("tcp://localhost:1883", c.urlMqtt)
        assertNull(c.utilisateurMqtt)
    }

    @Test
    fun `mqtt avec broker et identifiants`() {
        val c = ConfigDriver.depuisEnvironnement(mapOf(
            "THERMOBOX_DRIVER" to " MQTT ",
            "THERMOBOX_MQTT_URL" to "ssl://broker.example:8883",
            "THERMOBOX_MQTT_USER" to "thermobox",
            "THERMOBOX_MQTT_PASSWORD" to "s3cret ",
        ))
        assertEquals(TypeDriver.MQTT, c.type)
        assertEquals("ssl://broker.example:8883", c.urlMqtt)
        assertEquals("thermobox", c.utilisateurMqtt)
        assertEquals("s3cret ", c.motDePasseMqtt)
    }

    @Test
    fun `valeurs invalides refusees au demarrage`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDriver.depuisEnvironnement(mapOf("THERMOBOX_DRIVER" to "modbus"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDriver.depuisEnvironnement(mapOf("THERMOBOX_MQTT_URL" to "localhost:1883"))
        }
    }
}
