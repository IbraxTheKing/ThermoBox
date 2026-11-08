package ax.ibr.thermobox.restserver.branding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ServerNameTest {

    @Test
    fun `a name is kept in the data folder, trimmed, and removed when blank`() {
        val dir = Files.createTempDirectory("tb-name")
        val names = ServerName(dir)
        assertNull(names.get())
        assertEquals("Siège social", names.set("  Siège   social "))
        assertEquals("Siège social", ServerName(dir).get())
        assertNull(names.set("   "))
        assertNull(names.get())
        names.set("Maison")
        assertNull(names.set(null))
        assertNull(names.get())
    }

    @Test
    fun `too long or with control characters, refused`() {
        val names = ServerName(Files.createTempDirectory("tb-name"))
        assertThrows(IllegalArgumentException::class.java) { names.set("x".repeat(ServerName.MAX_LENGTH + 1)) }
        assertThrows(IllegalArgumentException::class.java) { names.set("Bureau\u0000") }
        assertThrows(IllegalArgumentException::class.java) { names.set("Bureau‮evil") }
        assertEquals("é".repeat(ServerName.MAX_LENGTH), names.set("é".repeat(ServerName.MAX_LENGTH)))
        assertNull(ServerName(Files.createTempDirectory("tb-name")).get())
    }
}
