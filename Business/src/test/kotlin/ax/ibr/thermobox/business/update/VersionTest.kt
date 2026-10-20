package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.TypeMiseAJour
import ax.ibr.thermobox.common.update.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTest {

    private fun v(t: String) = Version.parse(t)!!

    @Test
    fun `lit les tags de ThermoBox`() {
        assertEquals("26.10.1", v("26.10.1").tag)
        assertEquals("nightly", v("26.10.2-nightly-1").prerelease)
        assertEquals(1, v("26.10.2-nightly-1").numeroPrerelease)
        assertEquals("beta", v("26.11.0-beta.2").prerelease)
        assertEquals("rc", v("26.10.0-rc").prerelease)
        assertEquals("26.10.1", v("v26.10.1").tag)
        assertNull(Version.parse("26.13.0"))
        assertNull(Version.parse("nightly-20261012"))
        assertNull(Version.parse("26.10.1/../../etc"))
        assertNull(Version.parse("dev"))
    }

    @Test
    fun `ordre des versions et des prepublications`() {
        val ordre = listOf("26.10.0-nightly-3", "26.10.0-beta.1", "26.10.0-beta.2", "26.10.0-rc.0", "26.10.0",
            "26.10.1-nightly-1", "26.10.1", "26.11.0-beta.1", "26.11.0", "27.1.0").map { v(it) }
        assertEquals(ordre, ordre.shuffled().sorted())
        assertEquals(v("26.01.0"), v("26.1.0"))
        assertEquals("26.01", v("26.1.0").serie)
    }

    @Test
    fun `corrective dans la serie, fonctionnelle sinon`() {
        assertEquals(TypeMiseAJour.CORRECTIVE, v("26.10.2").typeDepuis(v("26.10.1")))
        assertEquals(TypeMiseAJour.CORRECTIVE, v("26.10.2-nightly-1").typeDepuis(v("26.10.1")))
        assertEquals(TypeMiseAJour.FONCTIONNELLE, v("26.11.0").typeDepuis(v("26.10.1")))
        assertEquals(TypeMiseAJour.FONCTIONNELLE, v("27.10.0").typeDepuis(v("26.10.1")))
        assertEquals(TypeMiseAJour.FONCTIONNELLE, v("26.10.2").typeDepuis(null))
        assertTrue(v("26.11.0-rc.1").estPrerelease)
        assertFalse(v("26.11.0").estPrerelease)
    }
}
