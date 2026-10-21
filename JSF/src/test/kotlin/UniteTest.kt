import ax.ibr.thermobox.jsf.util.Unite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UniteTest {

    @Test
    fun `conversions et textes`() {
        assertEquals(68f, Unite.FAHRENHEIT.depuisCelsius(20f), 1e-4f)
        assertEquals(20f, Unite.FAHRENHEIT.versCelsius(68f), 1e-4f)
        assertEquals(21.5f, Unite.CELSIUS.depuisCelsius(21.5f))
        assertEquals("70,7", Unite.FAHRENHEIT.texte(21.5f))
        assertEquals("21,5", Unite.CELSIUS.texte(21.5))
        assertEquals("—", Unite.FAHRENHEIT.texte(null))
        // A difference has no offset: +1 °C is +1.8 °F
        assertEquals("+1,8°", Unite.FAHRENHEIT.ecart(1f))
        assertEquals("-0,5°", Unite.CELSIUS.ecart(-0.5f))
        assertEquals("", Unite.CELSIUS.ecart(null))
    }

    @Test
    fun `celsius hors d'une page`() {
        assertEquals(Unite.CELSIUS, Unite.courante())
    }
}
