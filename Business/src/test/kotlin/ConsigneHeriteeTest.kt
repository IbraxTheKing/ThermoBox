package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Etage
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.HierarchieService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

class ConsigneHeriteeTest {

    private fun <T : Salle> T.avecId(id: Long) = apply { this.id = id }

    // Bâtiment B → Étage E → salles S1 (boîtier X1) et S2 (boîtier X2) ; salle S3 (boîtier X3) directement dans B.
    private val b = Batiment("B").avecId(1)
    private val e = Etage("E").avecId(2)
    private val s1 = Salle("S1").avecId(3)
    private val s2 = Salle("S2").avecId(4)
    private val s3 = Salle("S3").avecId(5)
    private val x1 = Boitier("X1").avecId(6)
    private val x2 = Boitier("X2").avecId(7)
    private val x3 = Boitier("X3").avecId(8)
    private val parents = mapOf(e to b, s1 to e, s2 to e, s3 to b, x1 to s1, x2 to s2, x3 to s3)

    private inner class FausseHierarchie : HierarchieService {
        override fun getRacines() = listOf(b)
        override fun getChildren(parent: Salle) = parents.filterValues { it.id == parent.id }.keys.sortedBy { it.id }
        override fun getParent(child: Salle) = parents.entries.firstOrNull { it.key.id == child.id }?.value
        override fun getChemin(salle: Salle): List<Salle> =
            generateSequence(getParent(salle)) { getParent(it) }.toList().asReversed()
        override fun getBoitiers(salle: Salle) = emptyList<Boitier>()
        override fun getSalles(salle: Salle) = emptyList<Salle>()
        override fun setParent(child: Salle, parent: Salle?) = Unit
    }

    /** Only what the consignes need; the rest is never called here. */
    private class FaussesTemperatures : SalleTempAttrService {
        val lignes = mutableListOf<SalleTempAttr>()
        override fun add(t: SalleTempAttr) { lignes += t }
        override fun getConsigneFromSalle(salle: Salle) = lignes
            .filter { it.salle.id == salle.id && it.temperature is Consigne }
            .maxByOrNull { it.temperature.date!! }?.temperature as Consigne?
        override fun getConsignesFromSalleTimed(salle: Salle, start: Instant, end: Instant) =
            lignes.filter { it.salle.id == salle.id }.mapNotNull { it.temperature as? Consigne }
        override fun update(t: SalleTempAttr) = TODO()
        override fun remove(t: SalleTempAttr) = TODO()
        override fun getAll() = lignes.toList()
        override fun getById(id: Long) = TODO()
        override fun getBySalle(salleId: Long) = TODO()
        override fun getBySalle(salle: Salle) = TODO()
        override fun getByTemperature(temperature: Temperature) = TODO()
        override fun getByTemperature(temperatureId: Long) = TODO()
        override fun getCurrentTemperatureFromSalle(salle: Salle, temperatureClass: Temperature) = TODO()
        override fun getCurrentTemperaturesFromSalle(salle: Salle) = TODO()
        override fun getTemperaturesFromSalleTimed(salle: Salle, start: Instant, end: Instant) = TODO()
        override fun getAverageTemperatureFromSalle(salle: Salle, temperatureClass: Temperature) = TODO()
        override fun getAverageTemperatureFromSalleTimed(salle: Salle, start: Instant, end: Instant) = TODO()
        override fun getAllByTime(start: Instant, end: Instant) = TODO()
    }

    private val temperatures = FaussesTemperatures()
    // No real box in tests: every send fails, which is enough to see which boxes were targeted.
    private val climat = ClimatServiceImpl(FausseHierarchie(), temperatures) { error("pas de boîtier") }

    private fun consigneDe(salle: Salle, valeur: Float, minutes: Long = 0) =
        temperatures.add(SalleTempAttr(salle, Consigne(valeur, LocalDateTime.of(2026, 1, 1, 8, 0).plusMinutes(minutes))))

    @Test
    fun `une salle sans consigne suit celle de son etage, sinon de son batiment`() {
        assertNull(climat.getConsigne(s1))
        consigneDe(b, 19f)
        assertEquals(19f, climat.getConsigne(s1)?.value)
        consigneDe(e, 20f)
        val effective = climat.getConsigneEffective(s1)!!
        assertEquals(20f, effective.consigne.value)
        assertEquals(e, effective.source)
        assertTrue(effective.heritee)
        assertEquals(19f, climat.getConsigne(s3)?.value)
    }

    @Test
    fun `sans forcer, les salles qui ont leur consigne la gardent avec leurs boitiers`() {
        consigneDe(s2, 23f)

        val envoi = climat.envoyerConsigne(b, 21f)

        assertEquals(listOf(s2), envoi.conservees.map { it.source })
        assertEquals(listOf(x1, x3), envoi.boitiers)
        assertEquals(21f, climat.getConsigne(s1)?.value)
        assertEquals(23f, climat.getConsigne(s2)?.value)
        assertEquals(envoi.boitiers, envoi.echecs)
    }

    @Test
    fun `en forcant, la consigne remplace celles des salles et va a tous les boitiers`() {
        consigneDe(s2, 23f)
        consigneDe(e, 18f)

        val envoi = climat.envoyerConsigne(b, 21f, forcer = true)

        assertTrue(envoi.conservees.isEmpty())
        assertEquals(listOf(e, s2), envoi.remplacees)
        assertEquals(listOf(x1, x2, x3), envoi.boitiers)
        listOf(e, s1, s2, s3).forEach { assertEquals(21f, climat.getConsigne(it)?.value) }
    }

    @Test
    fun `apres une consigne forcee, les salles suivent de nouveau le batiment`() {
        consigneDe(s2, 23f)
        climat.envoyerConsigne(b, 21f, forcer = true)

        assertTrue(climat.getConsignesPropres(b).isEmpty())
        assertTrue(climat.getConsigneEffective(s2)!!.heritee)

        // A later consigne, not forced, reaches them all.
        val envoi = climat.envoyerConsigne(b, 19f)
        assertTrue(envoi.conservees.isEmpty())
        assertEquals(listOf(x1, x2, x3), envoi.boitiers)
        listOf(e, s1, s2, s3).forEach { assertEquals(19f, climat.getConsigne(it)?.value) }
    }

    @Test
    fun `seules les salles et etages comptent comme consignes propres`() {
        consigneDe(x1, 25f) // last consigne a box received
        consigneDe(s2, 23f)
        assertEquals(listOf(s2), climat.getConsignesPropres(b).map { it.source })
        assertFalse(climat.getConsignesPropres(s1).any())
    }
}
