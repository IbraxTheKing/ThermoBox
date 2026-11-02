package ax.ibr.thermobox.business.users

import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class FirstAdminTest {

    @Test
    fun `an empty database gets a provisional admin, its password in a private file only`() {
        val folder = Files.createTempDirectory("tb-first")
        val users = FakeUsers()
        val message = FirstAdmin.ensure(users, emptyMap(), folder)!!
        val admin = users.lines.single()
        assertEquals("admin", admin.username)
        assertEquals(UserType.ADMIN, admin.type)
        assertTrue(admin.passwordChangeRequired)
        assertNull(admin.provisionalExpiresAt)
        val file = folder.resolve("first-admin-password.txt")
        val password = Regex("provisional password: (\\S+)").find(Files.readString(file))!!.groupValues[1]
        assertFalse(message.contains(password))
        // The fake keeps what it is given: here the clear password (UserServiceImpl hashes it)
        assertEquals(password, admin.password)
        if (file.fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        }
        FirstAdmin.forget(admin, folder)
        assertFalse(Files.exists(file))
    }

    @Test
    fun `nothing is created when there are accounts, the name and password can be given`() {
        val folder = Files.createTempDirectory("tb-first")
        val users = FakeUsers().apply { add(User("bob", PasswordHasher.hash("x"), UserType.VIEWER)) }
        assertNull(FirstAdmin.ensure(users, emptyMap(), folder))
        assertEquals(1, users.lines.size)

        val empty = FakeUsers()
        FirstAdmin.ensure(empty, mapOf("THERMOBOX_ADMIN_USERNAME" to "chef", "THERMOBOX_ADMIN_PASSWORD" to "Donne-par-env1"), folder)
        assertEquals("chef", empty.lines.single().username)
        assertEquals("Donne-par-env1", empty.lines.single().password)
        assertTrue(empty.lines.single().passwordChangeRequired)
        assertFalse(Files.exists(folder.resolve("first-admin-password.txt")))
    }
}
