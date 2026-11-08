package ax.ibr.thermobox.business.audit

import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import ax.ibr.thermobox.persistence.dataservices.AuditDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

class AuditLogTest {

    private val now = LocalDateTime.of(2026, 11, 8, 12, 0)

    private class Memory : AuditDataService {
        val rows = CopyOnWriteArrayList<AuditEntry>()
        override fun add(entry: AuditEntry) = entry.copy(id = rows.size + 1L).also { rows += it }
        override fun find(query: AuditQuery) = rows.reversed().drop(query.offset).take(query.limit)
        override fun count(query: AuditQuery) = rows.size.toLong()
        override fun users() = rows.map { it.userId to it.username }.distinct()
        override fun purge(before: LocalDateTime): Int = rows.filter { it.at < before }.also { rows.removeAll(it.toSet()) }.size
    }

    private fun entry(daysAgo: Long) = AuditEntry(at = now.minusDays(daysAgo), userId = 1, username = "admin", role = "ADMIN", action = "setpoint.set")

    @Test
    fun `entries are written aside, kept 90 days by default, then purged`() {
        val store = Memory()
        val log = AuditLog({ store }, Files.createTempDirectory("tb-audit").resolve("audit.properties"), clock = { now })
        assertEquals(90, log.retentionDays())
        log.record(entry(100))
        log.record(entry(10))
        log.stop()
        assertEquals(2, store.rows.size)
        assertEquals(1, log.purge())
        assertEquals(listOf(now.minusDays(10)), store.rows.map { it.at })
    }

    @Test
    fun `the retention is chosen, kept in the data folder, or for good`() {
        val store = Memory()
        val file = Files.createTempDirectory("tb-audit").resolve("audit.properties")
        val log = AuditLog({ store }, file, clock = { now })
        store.add(entry(10))
        log.setRetention(5)
        assertEquals(5, AuditLog({ store }, file).retentionDays())
        assertTrue(store.rows.isEmpty(), "older than 5 days: removed at once")
        log.setRetention(null)
        assertNull(AuditLog({ store }, file).retentionDays())
        store.add(entry(5000))
        assertEquals(0, log.purge())
        assertThrows(IllegalArgumentException::class.java) { log.setRetention(0) }
        assertThrows(IllegalArgumentException::class.java) { log.setRetention(AuditLog.MAX_DAYS + 1) }
    }

    @Test
    fun `a database that refuses the entry never fails the caller`() {
        val failing = object : AuditDataService by Memory() {
            override fun add(entry: AuditEntry): AuditEntry = error("database down")
        }
        val log = AuditLog({ failing }, Files.createTempDirectory("tb-audit").resolve("audit.properties"), clock = { now })
        log.record(entry(1))
        log.stop()
    }
}
