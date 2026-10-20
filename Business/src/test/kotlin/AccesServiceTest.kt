package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Acces
import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Etage
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.HierarchieService
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.AccesDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime

class AccesServiceTest {

    private fun <T : Salle> T.avecId(id: Long) = apply { this.id = id }

    // Bâtiment A → Étage A1 → salles S1 (boîtier X1), S2 ; Bâtiment B → salle S3 ; salle S4 non rattachée.
    private val a = Batiment("A").avecId(1)
    private val a1 = Etage("A1").avecId(2)
    private val s1 = Salle("S1").avecId(3)
    private val s2 = Salle("S2").avecId(4)
    private val x1 = Boitier("X1").avecId(5)
    private val b = Batiment("B").avecId(6)
    private val s3 = Salle("S3").avecId(7)
    private val s4 = Salle("S4").avecId(8)
    private val tous = listOf(a, a1, s1, s2, x1, b, s3, s4)
    private val enfants = mapOf(1L to listOf(a1), 2L to listOf(s1, s2), 3L to listOf(x1), 6L to listOf(s3))
    private val parents = enfants.flatMap { (p, l) -> l.map { it.id!! to tous.first { s -> s.id == p } } }.toMap()

    private val hierarchie = object : HierarchieService {
        override fun getRacines() = listOf(a, b, s4)
        override fun getChildren(parent: Salle) = enfants[parent.id].orEmpty()
        override fun getParent(child: Salle) = parents[child.id]
        override fun getChemin(salle: Salle): List<Salle> = generateSequence(parents[salle.id]) { parents[it.id] }.toList().asReversed()
        override fun getBoitiers(salle: Salle) = emptyList<Boitier>()
        override fun getSalles(salle: Salle) = emptyList<Salle>()
        override fun setParent(child: Salle, parent: Salle?) = Unit
    }

    /** The accesses tables, in memory. */
    private class FauxAcces : AccesDataService {
        val lignes = mutableListOf<Acces>()
        val restreints = mutableSetOf<Long>()
        override fun getByUser(user: User) = lignes.filter { it.user.id == user.id }
        override fun estRestreint(user: User) = user.id in restreints
        override fun remplacer(user: User, restreint: Boolean, salleIds: Collection<Long>) {
            lignes.removeIf { it.user.id == user.id }
            restreints -= user.id!!
            if (restreint) {
                restreints += user.id!!
                salleIds.forEach { lignes += Acces(user, Salle().apply { id = it }) }
            }
        }
        override fun supprimerPourSalle(salle: Salle) { lignes.removeIf { it.salle.id == salle.id } }
        override fun supprimerPourUtilisateur(user: User) { lignes.removeIf { it.user.id == user.id }; restreints -= user.id!! }
        override fun add(t: Acces) { lignes += t }
        override fun update(t: Acces) = Unit
        override fun remove(t: Acces) { lignes -= t }
        override fun getAll() = lignes.toList()
        override fun getById(id: Long) = lignes.firstOrNull { it.id == id }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stub(type: Class<T>, reponses: Map<String, (Array<Any?>) -> Any?>): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, args ->
            val reponse = reponses[m.name] ?: throw UnsupportedOperationException(m.name)
            reponse(args ?: emptyArray())
        } as T

    private val salles = stub(SalleService::class.java, mapOf("getById" to { args -> tous.firstOrNull { it.id == args[0] } }))

    private val m1 = Mesurer(20f, LocalDateTime.now()).apply { id = 100 }
    private val m3 = Mesurer(22f, LocalDateTime.now()).apply { id = 101 }
    private val salleTemps = stub(SalleTempAttrService::class.java, mapOf("getAll" to { _ ->
        listOf(SalleTempAttr(x1, m1), SalleTempAttr(s3, m3))
    }))

    private val donnees = FauxAcces()
    private val service = AccesServiceImpl(donnees, hierarchie, salles) { salleTemps }

    private fun user(id: Long, type: UserType) = User("u$id", "x", type).apply { this.id = id }

    @Test
    fun `sans restriction, un compte existant voit tout`() {
        val p = service.perimetre(user(10, UserType.VIEWER))
        assertTrue(p.tout)
        assertTrue(p.peutVoir(s4))
    }

    @Test
    fun `un nouveau compte ne voit rien, un administrateur toujours tout`() {
        val u = user(11, UserType.GESTIONNAIRE)
        service.initialiser(u)
        assertFalse(service.perimetre(u).peutVoir(a))
        assertEquals(0, service.perimetre(u).nombreAccessibles)

        val admin = user(12, UserType.ADMIN)
        donnees.restreints += 12L
        assertTrue(service.perimetre(admin).tout)
        assertThrows(IllegalArgumentException::class.java) { service.definir(admin, true, listOf(s1)) }
    }

    @Test
    fun `un acces couvre le contenu, les ancetres ne sont que visibles`() {
        val u = user(13, UserType.VIEWER)
        service.definir(u, true, listOf(a1))
        val p = service.perimetre(u)
        listOf(a1, s1, s2, x1).forEach { assertTrue(p.peutVoir(it), it.name) }
        assertFalse(p.peutVoir(a))   // the bâtiment: name only
        assertTrue(p.estVisible(a))
        listOf(b, s3, s4).forEach { assertFalse(p.estVisible(it), it.name) }
    }

    @Test
    fun `les acces s'additionnent et une modification s'applique aussitot`() {
        val u = user(14, UserType.GESTIONNAIRE)
        service.definir(u, true, listOf(s1, b))
        val p = service.perimetre(u)
        assertTrue(p.peutVoir(x1))
        assertTrue(p.peutVoir(s3))
        assertFalse(p.peutVoir(s2))

        service.definir(u, true, listOf(s2))
        val apres = service.perimetre(u)
        assertFalse(apres.peutVoir(s1))
        assertFalse(apres.peutVoir(s3))
        assertTrue(apres.peutVoir(s2))

        service.definir(u, false, emptyList())
        assertTrue(service.perimetre(u).tout)
    }

    @Test
    fun `element inconnu refuse, temperatures filtrees`() {
        val u = user(15, UserType.VIEWER)
        assertThrows(IllegalArgumentException::class.java) { service.definir(u, true, listOf(Salle("?").avecId(999))) }

        service.definir(u, true, listOf(s1))
        val visibles: List<Temperature> = service.filtrerTemperatures(service.perimetre(u), listOf(m1, m3))
        assertEquals(listOf(m1), visibles)
    }
}
