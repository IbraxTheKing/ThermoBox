package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.UpdateType
import ax.ibr.thermobox.common.update.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTest {

    private fun v(t: String) = Version.parse(t)!!

    @Test
    fun `reads the ThermoBox tags`() {
        assertEquals("26.10.1", v("26.10.1").tag)
        assertEquals("nightly", v("26.10.2-nightly-1").prerelease)
        assertEquals(1, v("26.10.2-nightly-1").prereleaseNumber)
        assertEquals("beta", v("26.11.0-beta.2").prerelease)
        assertEquals("rc", v("26.10.0-rc").prerelease)
        assertEquals("26.10.1", v("v26.10.1").tag)
        assertNull(Version.parse("26.13.0"))
        assertNull(Version.parse("nightly-20261012"))
        assertNull(Version.parse("26.10.1/../../etc"))
        assertNull(Version.parse("dev"))
    }

    @Test
    fun `order of versions and pre-releases`() {
        val order = listOf("26.10.0-nightly-3", "26.10.0-beta.1", "26.10.0-beta.2", "26.10.0-rc.0", "26.10.0",
            "26.10.1-nightly-1", "26.10.1", "26.11.0-beta.1", "26.11.0", "27.1.0").map { v(it) }
        assertEquals(order, order.shuffled().sorted())
        assertEquals(v("26.01.0"), v("26.1.0"))
        assertEquals("26.01", v("26.1.0").series)
    }

    @Test
    fun `patch within the series, feature otherwise`() {
        assertEquals(UpdateType.PATCH, v("26.10.2").typeFrom(v("26.10.1")))
        assertEquals(UpdateType.PATCH, v("26.10.2-nightly-1").typeFrom(v("26.10.1")))
        assertEquals(UpdateType.FEATURE, v("26.11.0").typeFrom(v("26.10.1")))
        assertEquals(UpdateType.FEATURE, v("27.10.0").typeFrom(v("26.10.1")))
        assertEquals(UpdateType.FEATURE, v("26.10.2").typeFrom(null))
        assertTrue(v("26.11.0-rc.1").isPrerelease)
        assertFalse(v("26.11.0").isPrerelease)
    }
}
