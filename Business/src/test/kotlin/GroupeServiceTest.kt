package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.persistence.dataservices.GroupeDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class GroupeServiceTest {

    private val s1 = Salle("S1").apply { id = 1 }
    private val x1 = Boitier("X1").apply { id = 2 }
    private val alice = User("alice", "x", UserType.VIEWER).apply { id = 10 }

    private val groupes = ArrayList<Groupe>()
    private val remplacements = ArrayList<Triple<Long?, Collection<Long>, Collection<Long>>>()

    private val donnees = Proxy.newProxyInstance(GroupeDataService::class.java.classLoader, arrayOf(GroupeDataService::class.java)) { _, m, a ->
        when (m.name) {
            "getByNom" -> groupes.firstOrNull { it.nom == a[0] }
            "getById" -> groupes.firstOrNull { it.id == a[0] }
            "add" -> (a[0] as Groupe).also { it.id = groupes.size + 1L; groupes += it }.let { Unit }
            "update" -> Unit
            "remplacer" -> { @Suppress("UNCHECKED_CAST") remplacements += Triple((a[0] as Groupe).id, a[1] as Collection<Long>, a[2] as Collection<Long>); Unit }
            else -> throw UnsupportedOperationException(m.name)
        }
    } as GroupeDataService

    private val salles = Proxy.newProxyInstance(SalleService::class.java.classLoader, arrayOf(SalleService::class.java)) { _, m, a ->
        when (m.name) {
            "getById" -> listOf(s1, x1).firstOrNull { it.id == a[0] }
            else -> throw UnsupportedOperationException(m.name)
        }
    } as SalleService

    private val service = GroupeServiceImpl(donnees, salles)

    @Test
    fun `creation avec membres et elements, nom unique`() {
        val g = service.enregistrer(null, "  Accueil  ", null, listOf(alice), listOf(s1))
        assertEquals("Accueil", g.nom)
        assertEquals(Triple(g.id, listOf(10L), listOf(1L)), remplacements.single().let { Triple(it.first, it.second.toList(), it.third.toList()) })
        assertThrows(IllegalArgumentException::class.java) { service.enregistrer(null, "Accueil", null, emptyList(), emptyList()) }
        // Same name for the same group: fine (renaming nothing)
        service.enregistrer(g, "Accueil", "Hall", emptyList(), emptyList())
    }

    @Test
    fun `nom obligatoire, pas de boitier, element connu`() {
        assertThrows(IllegalArgumentException::class.java) { service.enregistrer(null, " ", null, emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { service.enregistrer(null, "B", null, emptyList(), listOf(x1)) }
        assertThrows(IllegalArgumentException::class.java) { service.enregistrer(null, "C", null, emptyList(), listOf(Salle("?").apply { id = 99 })) }
    }
}
