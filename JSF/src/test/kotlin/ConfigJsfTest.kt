import ax.ibr.thermobox.jsf.config.ConfigJsf
import jakarta.faces.application.ProjectStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigJsfTest {

    @Test
    fun `production par defaut`() {
        assertEquals(ProjectStage.Production, ConfigJsf.stage(emptyMap()))
        assertEquals(ProjectStage.Development, ConfigJsf.stage(mapOf("THERMOBOX_STAGE" to "development")))
        assertThrows(IllegalArgumentException::class.java) { ConfigJsf.stage(mapOf("THERMOBOX_STAGE" to "prod")) }
    }

    @Test
    fun `https force par variable`() {
        assertFalse(ConfigJsf.https(emptyMap()))
        assertTrue(ConfigJsf.https(mapOf("THERMOBOX_HTTPS" to "TRUE")))
    }
}
