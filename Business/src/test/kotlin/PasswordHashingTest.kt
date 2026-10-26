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
    private class FakeUsers : UserService {
        val lines = mutableListOf<User>()
        override fun getByUsername(username: String) = lines.firstOrNull { it.username == username }
        override fun getByType(userType: UserType) = lines.filter { it.type == userType }
        override fun add(t: User) { lines += t }
        override fun update(t: User) { lines.replaceAll { if (it.username == t.username) t else it } }
        override fun remove(t: User) { lines -= t }
        override fun getAll() = lines.toList()
        override fun getById(id: Long) = lines.firstOrNull { it.id == id }
    }

    @Test
    fun `the hash is salted and verifies`() {
        val a = PasswordHasher.hash("motdepasse123")
        val b = PasswordHasher.hash("motdepasse123")

        assertTrue(PasswordHasher.isHash(a))
        assertNotEquals(a, b) // random salt
        assertTrue(PasswordHasher.matches("motdepasse123", a))
        assertFalse(PasswordHasher.matches("MotDePasse123", a))
        assertFalse(PasswordHasher.matches("motdepasse123", "not a hash"))
    }

    @Test
    fun `a password longer than 72 bytes is refused`() {
        assertThrows(IllegalArgumentException::class.java) { PasswordHasher.hash("é".repeat(37)) } // 74 bytes
        assertFalse(PasswordHasher.matches("a".repeat(80), PasswordHasher.hash("a".repeat(72))))
    }

    @Test
    fun `the service only stores hashes and never rehashes a hash`() {
        val data = FakeUsers()
        val service = UserServiceImpl(data)

        service.add(User("alice", "secret-alice"))
        val stored = data.lines.single().password!!
        assertTrue(PasswordHasher.matches("secret-alice", stored))

        // Re-saved unchanged (e.g. a new username): the hash is kept, not hashed again.
        service.update(data.lines.single())
        assertSame(stored, data.lines.single().password)

        // Password changed in clear text: hashed again.
        service.update(User("alice", "new-secret"))
        assertTrue(PasswordHasher.matches("new-secret", data.lines.single().password!!))
    }

    @Test
    fun `login with a hash, and migration of an old clear-text password`() {
        val data = FakeUsers()
        val service = UserServiceImpl(data)
        service.add(User("alice", "secret-alice"))
        data.add(User("bob", "old-clear-text")) // written before hashing existed

        assertEquals("alice", Authenticator.authenticate("alice", "secret-alice", service)?.username)
        assertNull(Authenticator.authenticate("alice", "wrong", service))
        assertNull(Authenticator.authenticate("unknown", "secret-alice", service))

        assertNull(Authenticator.authenticate("bob", "wrong", service))
        assertEquals("bob", Authenticator.authenticate("bob", "old-clear-text", service)?.username)
        val migrated = data.getByUsername("bob")!!.password
        assertTrue(PasswordHasher.isHash(migrated)) // stored as a hash from now on
        assertEquals("bob", Authenticator.authenticate("bob", "old-clear-text", service)?.username)
    }
}
