package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.FavoriService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FavoriServiceTest {

    /** In-memory stand-in for the JPA data service. */
    private class FauxFavoris : FavoriService {
        val lignes = mutableListOf<Favori>()
        override fun getByUser(user: User) = lignes.filter { it.user.id == user.id }
        override fun getBySalle(salle: Salle) = lignes.filter { it.salle.id == salle.id }
        override fun add(t: Favori) { lignes += t }
        override fun update(t: Favori) {}
        override fun remove(t: Favori) { lignes -= t }
        override fun getAll() = lignes.toList()
        override fun getById(id: Long) = lignes.firstOrNull { it.id == id }
    }

    private val alice = User("alice", "x").apply { id = 1 }
    private val bob = User("bob", "x").apply { id = 2 }
    private val salle = Salle("Accueil").apply { id = 10 }

    @Test
    fun `une salle n'est ajoutee qu'une fois aux favoris d'un utilisateur`() {
        val donnees = FauxFavoris()
        val service = FavoriServiceImpl(donnees)

        service.add(Favori(alice, salle))
        service.add(Favori(alice, salle))
        service.add(Favori(bob, salle))

        assertEquals(1, service.getByUser(alice).size)
        assertEquals(2, service.getBySalle(salle).size)
    }
}
