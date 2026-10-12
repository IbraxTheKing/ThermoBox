package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.business.rules.ConsigneRules
import ax.ibr.thermobox.business.rules.HierarchieRules
import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Etage
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.HistoriqueBoitier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime

class HierarchieClimatTest {

    private fun <T : Salle> T.avecId(id: Long) = apply { this.id = id }

    private val batiment = Batiment("B").avecId(1)
    private val etage = Etage("E").avecId(2)
    private val salle = Salle("S").avecId(3)
    private val boitier = Boitier("X").avecId(4)

    @Test
    fun `liens autorises, dont une salle directement dans un batiment`() {
        assertDoesNotThrow { HierarchieRules.checkLink(batiment, etage) }
        assertDoesNotThrow { HierarchieRules.checkLink(etage, salle) }
        assertDoesNotThrow { HierarchieRules.checkLink(batiment, salle) }
        assertDoesNotThrow { HierarchieRules.checkLink(salle, boitier) }
    }

    @Test
    fun `les liens qui sautent ou inversent un niveau sont refuses`() {
        listOf(salle to etage, etage to batiment, batiment to boitier, etage to boitier, salle to salle).forEach { (p, c) ->
            assertThrows(IllegalArgumentException::class.java) { HierarchieRules.checkLink(p, c) }
        }
    }

    @Test
    fun `la moyenne garde la derniere valeur de chaque boitier`() {
        val t0 = LocalDateTime.of(2026, 1, 1, 10, 0)
        val historiques = listOf(
            HistoriqueBoitier(Boitier("A").avecId(10), listOf(Mesurer(20f, t0), Mesurer(22f, t0.plusMinutes(10)))),
            HistoriqueBoitier(Boitier("B").avecId(11), listOf(Mesurer(18f, t0.plusMinutes(5)))),
        )

        val moyenne = ClimatServiceImpl.moyenneGlissante(historiques)

        // 10h00 : A seul ; 10h05 : (20 + 18) / 2 ; 10h10 : (22 + 18) / 2
        assertEquals(listOf(20f, 19f, 20f), moyenne.map { it.value })
        assertEquals(listOf(t0, t0.plusMinutes(5), t0.plusMinutes(10)), moyenne.map { it.date })
    }

    @Test
    fun `une consigne hors limites est refusee`() {
        assertDoesNotThrow { ConsigneRules.check(21f) }
        assertThrows(TooHotException::class.java) { ConsigneRules.check(60f) }
        assertThrows(ImpossibleValueException::class.java) { ConsigneRules.check(150f) }
        assertThrows(ImpossibleValueException::class.java) { ConsigneRules.check(-20f) }
    }
}
