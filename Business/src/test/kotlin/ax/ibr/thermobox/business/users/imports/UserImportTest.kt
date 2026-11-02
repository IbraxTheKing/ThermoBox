package ax.ibr.thermobox.business.users.imports

import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.business.users.FakeUsers
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class UserImportTest {

    private val columns = listOf("Identifiant", "Prénom", "Nom de famille", "Adresse e-mail", "Rôle", "Service")

    private fun table(vararg rows: List<String>) = ImportTable(columns, rows.toList())

    private val mapping = mapOf(
        ImportField.USERNAME to "Identifiant",
        ImportField.FIRST_NAME to "Prénom",
        ImportField.LAST_NAME to "Nom de famille",
        ImportField.EMAIL to "Adresse e-mail",
        ImportField.ROLE to "Rôle",
    )

    @Test
    fun `the columns are suggested from their names`() {
        assertEquals(mapping, UserImport.suggest(columns))
        assertEquals(
            mapOf(ImportField.FIRST_NAME to "first_name", ImportField.LAST_NAME to "last name", ImportField.EMAIL to "Mail", ImportField.USERNAME to "login"),
            UserImport.suggest(listOf("login", "first_name", "last name", "Mail", "Bureau")),
        )
    }

    @Test
    fun `every line is checked against the accounts and the lines before it`() {
        val users = FakeUsers().apply {
            add(User("jdupont", "h", UserType.VIEWER).apply { email = "pris@ex.fr" })
        }
        val rows = UserImport.plan(
            table(
                listOf("", "Jean", "Dupont", "jean.dupont@ex.fr", "", "RH"),          // name made: jdupont taken → jeandupont
                listOf("m.durand", "Marie", "Durand", "marie@ex.fr", "Gestionnaire", ""), // adjusted: mdurand
                listOf("mdurand", "Marc", "Durand", "marc@ex.fr", "", ""),            // repeated in the list
                listOf("pmartin", "Paul", "Martin", "PRIS@ex.fr", "", ""),            // address taken
                listOf("x2", "Xavier", "", "pas-une-adresse", "chef", ""),             // invalid three times
                listOf("", "", "", "", "", "Accueil"),                                 // nothing to make a name of
                listOf("ÉLiane", "Éliane", "Roux", "", "admin", ""),
            ),
            ImportOptions(mapping, defaultRole = UserType.VIEWER),
            users,
        )
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), rows.map { it.line })
        with(rows[0]) { assertEquals(ImportRow.Status.CREATE, status); assertEquals("jeandupont", username); assertTrue(usernameMade); assertEquals(UserType.VIEWER, role) }
        with(rows[1]) { assertEquals(ImportRow.Status.CREATE, status); assertEquals("mdurand", username); assertTrue(usernameAdjusted); assertEquals(UserType.MANAGER, role) }
        with(rows[2]) { assertEquals(ImportRow.Status.DUPLICATE, status); assertEquals(listOf("username_repeated"), problems) }
        with(rows[3]) { assertEquals(ImportRow.Status.DUPLICATE, status); assertEquals(listOf("email_taken"), problems) }
        with(rows[4]) { assertEquals(ImportRow.Status.INVALID, status); assertEquals(setOf("email_invalid", "role_unknown", "username_invalid"), problems.toSet()) }
        with(rows[5]) { assertEquals(ImportRow.Status.INVALID, status); assertEquals(listOf("username_missing"), problems) }
        with(rows[6]) { assertEquals(ImportRow.Status.CREATE, status); assertEquals("ELiane", username); assertEquals(UserType.ADMIN, role) }
    }

    @Test
    fun `names are made free and in letters only`() {
        val taken = setOf("jdupont", "jeandupont", "jduponta")
        assertEquals("jdupontb", UserImport.make("Jean", "Dupont") { it in taken })
        assertEquals("jobrien", UserImport.make("Jean-Marc", "O'Brien") { false })
        assertEquals("doe", UserImport.make(null, "Doe") { false })
        assertEquals(null, UserImport.make(" ", "42") { false })
        assertEquals("JeanMarieOBrien", UserImport.letters("Jean-Marie O'Brien"))
        assertEquals("Loetitia", UserImport.letters("Lœtitia"))
    }

    @Test
    fun `the import creates provisional accounts and goes on after a failure`() {
        val users = FakeUsers(refuse = { it.username == "bmoreau" })
        val now = LocalDateTime.of(2026, 11, 2, 9, 0)
        val result = UserImport.apply(
            table(
                listOf("alambert", "Alice", "Lambert", "alice@ex.fr", "", ""),
                listOf("bmoreau", "Bruno", "Moreau", "", "", ""),
                listOf("cpetit", "Chloé", "Petit", "pas-une-adresse", "", ""),
                listOf("dleroy", "Denis", "Leroy", "", "manager", ""),
            ),
            ImportOptions(mapping, defaultRole = UserType.VIEWER, validityDays = 3),
            users,
            now,
        )
        assertEquals(listOf("alambert", "dleroy"), result.created.map { it.username })
        assertEquals(listOf(3 to "create_failed"), result.failed)
        assertEquals(listOf(4), result.skipped.map { it.line })
        // Lines numbered as in the file
        val withGap = ImportTable(columns, listOf(listOf("a", "Anne", "Aa", "", "", ""), List(6) { "" }, listOf("b", "Bob", "Bb", "", "", "")))
        assertEquals(listOf(2, 4), UserImport.plan(withGap, ImportOptions(mapping), FakeUsers()).map { it.line })
        val passwords = result.created.map { it.password }
        assertEquals(2, passwords.toSet().size)
        for ((account, user) in result.created.zip(users.lines)) {
            assertEquals(account.id, user.id)
            assertTrue(PasswordHasher.isHash(user.password)) // only the hash is kept
            assertTrue(PasswordHasher.matches(account.password, user.password!!))
            assertTrue(user.passwordChangeRequired)
            assertEquals(now.plusDays(3), user.provisionalExpiresAt)
        }
        assertEquals(UserType.MANAGER, users.getByUsername("dleroy")!!.type)
        assertEquals("Alice", users.getByUsername("alambert")!!.firstName)
        assertFalse(users.lines.any { it.username == "cpetit" })
    }
}
