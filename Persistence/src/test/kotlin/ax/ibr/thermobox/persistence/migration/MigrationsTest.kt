package ax.ibr.thermobox.persistence.migration

import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.CRC32

/** Checks the migration scripts without a database: names, order, and one table per entity. */
class MigrationsTest {

    private val directory = File(MigrationsTest::class.java.getResource("/db/migration")!!.toURI())
    private val scripts = directory.listFiles()!!.filter { it.isFile }.sortedBy { version(it) }

    private fun version(f: File): Int =
        Regex("""V(\d+)__\w+\.sql""").matchEntire(f.name)?.groupValues?.get(1)?.toInt()
            ?: error("Invalid migration name: ${f.name} (expected V<n>__<description>.sql)")

    @Test
    fun `consecutive versions starting at 1`() {
        assertEquals((1..scripts.size).toList(), scripts.map { version(it) })
    }

    @Test
    fun `every entity has its table in a migration`() {
        // Tables after every script: created, then possibly renamed (RENAME TABLE a TO b, c TO d)
        val created = LinkedHashSet<String>()
        for (f in scripts) {
            val sql = f.readLines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").lowercase()
            Regex("""create table if not exists `?(\w+)`?""").findAll(sql).forEach { created += it.groupValues[1] }
            Regex("""rename\s+table\s+([^;]+);""").findAll(sql).forEach { statement ->
                Regex("""`?(\w+)`?\s+to\s+`?(\w+)`?""").findAll(statement.groupValues[1]).forEach {
                    if (created.remove(it.groupValues[1])) created += it.groupValues[2]
                }
            }
        }
        val persistence = MigrationsTest::class.java.getResource("/META-INF/persistence.xml")!!.readText()
        val classes = Regex("""<class>\s*([\w.]+)\s*</class>""").findAll(persistence).map { it.groupValues[1] }.toList()
        assertTrue(classes.isNotEmpty())
        for (name in classes) {
            val table = Class.forName(name).getAnnotation(Table::class.java) ?: continue // subclass (single table)
            assertTrue(table.name.lowercase() in created, "No migration creates the table ${table.name} ($name)")
        }
    }

    @Test
    fun `migrations never drop anything silently`() {
        // DROP / RENAME break a downgrade: allowed only with an explicit marker in the script
        for (f in scripts) {
            val text = f.readText()
            val destructive = Regex("""(?i)\b(drop\s+(table|column)|rename\s+(table|column)|change\s+column)\b""").containsMatchIn(text)
            assertFalse(destructive && "-- destructive" !in text,
                "${f.name} drops or renames: add \"-- destructive\" and describe the migration in the release notes")
        }
    }

    /**
     * Checksums of the scripts already applied to databases (releases and nightlies), computed like
     * Flyway (CRC32 of the lines, without line breaks). Flyway refuses to start when an applied
     * script changed, even a comment: these scripts must stay byte for byte as they are. A new
     * script gets its line here once it has been published.
     */
    private val published = mapOf(
        1 to -1607158333,
        2 to -1479817308,
        3 to -1991719204,
        // Its first comment says 26.11 while it ships in 26.12: leave it, the nightlies applied it so.
        4 to -1067058891,
    )

    private fun flywayChecksum(f: File): Int {
        val crc = CRC32()
        f.readText(Charsets.UTF_8).removePrefix("\uFEFF").lines()
            .let { if (it.lastOrNull() == "") it.dropLast(1) else it }
            .forEach { crc.update(it.toByteArray(Charsets.UTF_8)) }
        return crc.value.toInt()
    }

    @Test
    fun `published migrations never change`() {
        for ((v, expected) in published) {
            val f = scripts.firstOrNull { version(it) == v } ?: error("Migration V$v is missing")
            assertEquals(expected, flywayChecksum(f),
                "${f.name} changed after it was applied to databases: Flyway will refuse to start. " +
                    "Put it back as it was and write a new migration instead.")
        }
    }

    @Test
    fun `disabled by an environment variable`() {
        assertTrue(DatabaseMigrations.enabled(emptyMap()))
        assertTrue(DatabaseMigrations.enabled(mapOf("THERMOBOX_DB_MIGRATE" to "true")))
        assertFalse(DatabaseMigrations.enabled(mapOf("THERMOBOX_DB_MIGRATE" to "false")))
        assertFalse(DatabaseMigrations.enabled(mapOf("THERMOBOX_DB_MIGRATE" to " OFF ")))
    }
}
