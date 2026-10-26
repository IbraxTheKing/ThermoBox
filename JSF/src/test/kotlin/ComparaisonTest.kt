import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.jsf.util.Comparaison
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class ComparaisonTest {

    private val debut = LocalDateTime.of(2026, 10, 21, 0, 0)
    private val fin = debut.plusHours(24)

    @Test
    fun `un point par tranche, moyenne des mesures, vide sans mesure`() {
        val pas = Comparaison.pas(debut, fin)
        assertEquals(Duration.ofMinutes(15), pas)
        val points = listOf(
            Measurement(20f, debut.plusMinutes(1)), Measurement(22f, debut.plusMinutes(14)),   // tranche 0 : 21
            Measurement(19f, debut.plusMinutes(31)),                                         // tranche 2
            Measurement(30f, debut.minusMinutes(5)),                                         // avant la période
        )
        val c = Comparaison.courbe(points, debut, fin, pas)
        assertEquals(96, c.valeurs.size)
        assertEquals(21f, c.valeurs[0])
        assertNull(c.valeurs[1])
        assertEquals(19f, c.valeurs[2])
        assertEquals(19f, c.min)
        assertEquals(22f, c.max)
        assertEquals(61f / 3, c.moyenne!!, 1e-4f)
        // 21 et 19 : seul 21 est à moins d'un demi-degré de 20,8
        assertEquals(.5f, c.dansLaCible(20.8f))
        assertNull(c.dansLaCible(null))
    }

    @Test
    fun `pas selon la duree`() {
        assertEquals(Duration.ofHours(1), Comparaison.pas(debut, debut.plusDays(7)))
        assertEquals(Duration.ofHours(4), Comparaison.pas(debut, debut.plusDays(30)))
    }
}
