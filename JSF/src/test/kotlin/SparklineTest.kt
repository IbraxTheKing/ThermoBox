package ax.ibr.thermobox.jsf.util

import ax.ibr.thermobox.common.entities.Temperature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SparklineTest {

    private val debut = LocalDateTime.of(2026, 10, 1, 0, 0)
    private val fin = debut.plusHours(24)

    private fun mesure(heures: Double, valeur: Float) =
        Temperature(valeur, debut.plusMinutes((heures * 60).toLong()))

    @Test
    fun `il faut au moins deux points`() {
        assertNull(Sparkline.of(emptyList(), debut, fin, 20f))
        assertNull(Sparkline.of(listOf(mesure(1.0, 20f), mesure(1.1, 21f)), debut, fin, 20f)) // same slot
    }

    @Test
    fun `les chemins restent dans la boite et utilisent le point decimal`() {
        val mesures = (0 until 24 * 4).map { mesure(it / 4.0, 19f + (it % 8) / 4f) }
        val s = Sparkline.of(mesures, debut, fin, 20f)!!

        assertTrue(s.ligne.startsWith("M"))
        assertTrue(',' !in s.ligne)
        val nombres = Regex("""-?\d+\.\d+""").findAll(s.ligne).map { it.value.toDouble() }.toList()
        assertTrue(nombres.all { it in 0.0..Sparkline.WIDTH })
        assertTrue(s.aire.endsWith("Z"))
        assertTrue(s.dernierY in 0.0..100.0)
        assertNotNull(s.consigneY)
    }

    @Test
    fun `une consigne trop eloignee ne compte pas dans l'echelle`() {
        val mesures = listOf(mesure(1.0, 20f), mesure(12.0, 21f))
        assertNull(Sparkline.of(mesures, debut, fin, 35f)!!.consigneY)
    }

    @Test
    fun `la variation porte sur la derniere heure`() {
        val mesures = listOf(mesure(10.0, 18f), mesure(22.9, 20f), mesure(23.9, 21f))
        val s = Sparkline.of(mesures, debut, fin, null)!!
        assertEquals(1f, s.variation!!, 0.01f)
        assertEquals("pi-arrow-up-right", s.variationIcon)
        assertEquals("+1,0° en 1 h", s.variationLabel)
    }
}
