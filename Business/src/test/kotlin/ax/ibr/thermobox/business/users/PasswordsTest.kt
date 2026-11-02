package ax.ibr.thermobox.business.users

import ax.ibr.thermobox.business.security.Authenticator
import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.business.security.PasswordPolicy
import ax.ibr.thermobox.business.security.ProvisionalPasswords
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class PasswordsTest {

    @Test
    fun `provisional passwords are random, readable and follow the policy`() {
        val all = List(2000) { ProvisionalPasswords.generate() }
        assertEquals(all.size, all.toSet().size)
        for (p in all) {
            assertTrue(Regex("^[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}$").matches(p), p)
            assertFalse(p.any { it in "0O1lI" }, p)
            assertEquals(emptyList<String>(), PasswordPolicy.problems(p, "dupont"), p)
        }
    }

    @Test
    fun `a provisional password must be changed and expires`() {
        val now = LocalDateTime.of(2026, 11, 2, 12, 0)
        val user = User("alice", "x", UserType.VIEWER)
        val password = ProvisionalPasswords.assign(user, Duration.ofDays(7), now)
        assertEquals(password, user.password)
        assertTrue(user.passwordChangeRequired)
        assertEquals(now.plusDays(7), user.provisionalExpiresAt)
        assertFalse(ProvisionalPasswords.expired(user, now.plusDays(6)))
        assertTrue(ProvisionalPasswords.expired(user, now.plusDays(8)))
        // Without a limit (the first admin): never out of date
        ProvisionalPasswords.assign(user, null, now)
        assertNull(user.provisionalExpiresAt)
        assertFalse(ProvisionalPasswords.expired(user, now.plusYears(3)))
    }

    @Test
    fun `the policy refuses short, figureless or name passwords`() {
        assertTrue("too_short" in PasswordPolicy.problems("abc123", "bob"))
        assertTrue("no_digit" in PasswordPolicy.problems("abcdefghijkl", "bob"))
        assertTrue("no_letter" in PasswordPolicy.problems("12345678901", "bob"))
        assertTrue("contains_username" in PasswordPolicy.problems("Dupont2026!", "dupont"))
        assertTrue("too_simple" in PasswordPolicy.problems("aaaaaaaaa1", "bob"))
        assertTrue("too_long" in PasswordPolicy.problems("a1".repeat(40), "bob"))
        assertEquals(emptyList<String>(), PasswordPolicy.problems("Rivière-Nord 42", "bob"))
    }

    @Test
    fun `changing the provisional password ends the provisional state and refuses it again`() {
        val users = FakeUsers()
        val user = User("alice", "x", UserType.VIEWER)
        val provisional = ProvisionalPasswords.assign(user)
        user.password = PasswordHasher.hash(provisional)
        users.add(user)

        assertEquals(PasswordChange.Result.WrongCurrent, PasswordChange.change(user, "wrong", "Nouveau-mot2passe", users))
        val same = PasswordChange.change(user, provisional, provisional, users)
        assertTrue(same is PasswordChange.Result.Refused && "same_as_current" in same.problems)
        val weak = PasswordChange.change(user, provisional, "court1", users)
        assertTrue(weak is PasswordChange.Result.Refused && "too_short" in weak.problems)
        assertTrue(user.passwordChangeRequired)

        assertEquals(PasswordChange.Result.Done, PasswordChange.change(user, provisional, "Nouveau-mot2passe", users))
        assertFalse(user.passwordChangeRequired)
        assertNull(user.provisionalExpiresAt)
        // The new password goes to the UserService (which hashes it), in place of the hash of the
        // provisional one: that one no longer opens the account
        assertEquals("Nouveau-mot2passe", users.getById(user.id!!)!!.password)
        assertFalse(users.lines.any { PasswordHasher.isHash(it.password) && PasswordHasher.matches(provisional, it.password!!) })
    }

    @Test
    fun `an expired provisional password is told apart at login`() {
        val users = FakeUsers()
        val user = User("alice", "x", UserType.VIEWER)
        val provisional = ProvisionalPasswords.assign(user, Duration.ofDays(1), LocalDateTime.now().minusDays(2))
        user.password = PasswordHasher.hash(provisional)
        users.add(user)
        assertEquals(Authenticator.Result.ProvisionalExpired, Authenticator.check("alice", provisional, users))
        assertEquals(Authenticator.Result.Refused, Authenticator.check("alice", "autre", users))
        user.provisionalExpiresAt = LocalDateTime.now().plusDays(1)
        assertTrue(Authenticator.check("alice", provisional, users) is Authenticator.Result.Accepted)
    }
}
