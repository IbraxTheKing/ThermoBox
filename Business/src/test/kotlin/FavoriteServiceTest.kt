package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.FavoriteService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FavoriteServiceTest {

    /** In-memory stand-in for the JPA data service. */
    private class FakeFavorites : FavoriteService {
        val lines = mutableListOf<Favorite>()
        override fun getByUser(user: User) = lines.filter { it.user.id == user.id }
        override fun getByRoom(room: Room) = lines.filter { it.room.id == room.id }
        override fun add(t: Favorite) { lines += t }
        override fun update(t: Favorite) {}
        override fun remove(t: Favorite) { lines -= t }
        override fun getAll() = lines.toList()
        override fun getById(id: Long) = lines.firstOrNull { it.id == id }
    }

    private val alice = User("alice", "x").apply { id = 1 }
    private val bob = User("bob", "x").apply { id = 2 }
    private val room = Room("Reception").apply { id = 10 }

    @Test
    fun `a room is added only once to a user's favorites`() {
        val data = FakeFavorites()
        val service = FavoriteServiceImpl(data)

        service.add(Favorite(alice, room))
        service.add(Favorite(alice, room))
        service.add(Favorite(bob, room))

        assertEquals(1, service.getByUser(alice).size)
        assertEquals(2, service.getByRoom(room).size)
    }
}
