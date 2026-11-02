package ax.ibr.thermobox.restserver.imports

import ax.ibr.thermobox.business.users.imports.ImportTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ImportSessionsTest {

    private val table = ImportTable(listOf("Nom"), listOf(listOf("Dupont")))

    @Test
    fun `a list is kept for its admin only, half an hour at most`() {
        val now = Instant.parse("2026-11-02T10:00:00Z")
        val s = ImportSessions.open(1, "liste.csv", table, now)
        assertNotNull(ImportSessions.get(s.id, 1, now.plusSeconds(60)))
        assertNull(ImportSessions.get(s.id, 2, now.plusSeconds(60)))
        assertNull(ImportSessions.get(s.id, 1, now.plusSeconds(31 * 60)))
    }

    @Test
    fun `an admin keeps a few lists at once, the oldest go`() {
        val now = Instant.parse("2026-11-02T11:00:00Z")
        val ids = (0 until 7).map { ImportSessions.open(3, "l$it.csv", table, now.plusSeconds(it.toLong())).id }
        assertEquals(listOf(false, false, true, true, true, true, true), ids.map { ImportSessions.get(it, 3, now.plusSeconds(10)) != null })
        ImportSessions.close(ids.last())
        assertNull(ImportSessions.get(ids.last(), 3, now.plusSeconds(10)))
    }
}
