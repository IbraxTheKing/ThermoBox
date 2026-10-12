package ax.ibr.thermobox.business.security

import ax.ibr.thermobox.business.implementations.UserServiceImpl
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordHashingTest {

    /** In-memory stand-in for the JPA data service: stores what it is given, as is. */
    private class FauxUsers : UserService {
        val lignes = mutableListOf<User>()
        override fun getByUsername(username: String) = lignes.firstOrNull { it.username == username }
        override fun getByType(userType: UserType) = lignes.filter { it.type == userType }
        override fun add(t: User) { lignes += t }
        override fun update(t: User) { lignes.replaceAll { if (it.username == t.username) t else it } }
        override fun remove(t: User) { lignes -= t }
        override fun getAll() = lignes.toList()
        override fun getById(id: Long) = lignes.firstOrNull { it.id == id }
    }

    @Test
    fun `le hash est sale et se verifie`() {
        val a = PasswordHasher.hash("motdepasse123")
        val b = PasswordHasher.hash("motdepasse123")

        assertTrue(PasswordHasher.isHash(a))
        assertNotEquals(a, b) // random salt
        assertTrue(PasswordHasher.matches("motdepasse123", a))
        assertFalse(PasswordHasher.matches("MotDePasse123", a))
        assertFalse(PasswordHasher.matches("motdepasse123", "pas un hash"))
    }

    @Test
    fun `un mot de passe de plus de 72 octets est refuse`() {
        assertThrows(IllegalArgumentException::class.java) { PasswordHasher.hash("é".repeat(37)) } // 74 bytes
        assertFalse(PasswordHasher.matches("a".repeat(80), PasswordHasher.hash("a".repeat(72))))
    }

    @Test
    fun `le service ne stocke que des hash et ne rehache pas un hash`() {
        val donnees = FauxUsers()
        val service = UserServiceImpl(donnees)

        service.add(User("alice", "secret-alice"))
        val stocke = donnees.lignes.single().password!!
        assertTrue(PasswordHasher.matches("secret-alice", stocke))

        // Re-saved unchanged (e.g. a new username): the hash is kept, not hashed again.
        service.update(donnees.lignes.single())
        assertSame(stocke, donnees.lignes.single().password)

        // Password changed in clear text: hashed again.
        service.update(User("alice", "nouveau-secret"))
        assertTrue(PasswordHasher.matches("nouveau-secret", donnees.lignes.single().password!!))
    }

    @Test
    fun `connexion avec un hash, et migration d'un ancien mot de passe en clair`() {
        val donnees = FauxUsers()
        val service = UserServiceImpl(donnees)
        service.add(User("alice", "secret-alice"))
        donnees.add(User("bob", "ancien-en-clair")) // written before hashing existed

        assertEquals("alice", Authenticator.authenticate("alice", "secret-alice", service)?.username)
        assertNull(Authenticator.authenticate("alice", "mauvais", service))
        assertNull(Authenticator.authenticate("inconnu", "secret-alice", service))

        assertNull(Authenticator.authenticate("bob", "mauvais", service))
        assertEquals("bob", Authenticator.authenticate("bob", "ancien-en-clair", service)?.username)
        val migre = donnees.getByUsername("bob")!!.password
        assertTrue(PasswordHasher.isHash(migre)) // stored as a hash from now on
        assertEquals("bob", Authenticator.authenticate("bob", "ancien-en-clair", service)?.username)
    }
}
