package ax.ibr.thermobox.business.config

import ax.ibr.thermobox.business.config.ConfigDriver.DriverType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigDriverTest {

    @Test
    fun `simulated by default`() {
        val c = ConfigDriver.fromEnvironment(emptyMap())
        assertEquals(DriverType.SIMULATED, c.type)
        assertEquals("tcp://localhost:1883", c.mqttUrl)
        assertNull(c.mqttUser)
    }

    @Test
    fun `mqtt with broker and credentials`() {
        val c = ConfigDriver.fromEnvironment(mapOf(
            "THERMOBOX_DRIVER" to " MQTT ",
            "THERMOBOX_MQTT_URL" to "ssl://broker.example:8883",
            "THERMOBOX_MQTT_USER" to "thermobox",
            "THERMOBOX_MQTT_PASSWORD" to "s3cret ",
        ))
        assertEquals(DriverType.MQTT, c.type)
        assertEquals("ssl://broker.example:8883", c.mqttUrl)
        assertEquals("thermobox", c.mqttUser)
        assertEquals("s3cret ", c.mqttPassword)
    }

    @Test
    fun `invalid values refused at startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDriver.fromEnvironment(mapOf("THERMOBOX_DRIVER" to "modbus"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDriver.fromEnvironment(mapOf("THERMOBOX_MQTT_URL" to "localhost:1883"))
        }
    }
}
