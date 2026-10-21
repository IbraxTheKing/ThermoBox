package ax.ibr.thermobox.persistence.migration

import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Checks the migration scripts without a database: names, order, and one table per entity. */
class MigrationsTest {

    private val dossier = File(MigrationsTest::class.java.getResource("/db/migration")!!.toURI())
    private val scripts = dossier.listFiles()!!.filter { it.isFile }.sortedBy { version(it) }

    private fun version(f: File): Int =
        Regex("""V(\d+)__\w+\.sql""").matchEntire(f.name)?.groupValues?.get(1)?.toInt()
            ?: error("Nom de migration invalide : ${f.name} (attendu V<n>__<description>.sql)")

    @Test
    fun `versions consecutives a partir de 1`() {
        assertEquals((1..scripts.size).toList(), scripts.map { version(it) })
    }

    @Test
    fun `chaque entite a sa table dans une migration`() {
        val sql = scripts.joinToString("\n") { it.readText() }.lowercase()
        val crees = Regex("""create table if not exists `?(\w+)`?""").findAll(sql).map { it.groupValues[1] }.toSet()
        val persistence = MigrationsTest::class.java.getResource("/META-INF/persistence.xml")!!.readText()
        val classes = Regex("""<class>\s*([\w.]+)\s*</class>""").findAll(persistence).map { it.groupValues[1] }.toList()
        assertTrue(classes.isNotEmpty())
        for (nom in classes) {
            val table = Class.forName(nom).getAnnotation(Table::class.java) ?: continue // subclass (single table)
            assertTrue(table.name.lowercase() in crees, "Aucune migration ne crée la table ${table.name} ($nom)")
        }
    }

    @Test
    fun `les migrations ne suppriment rien sans le dire`() {
        // DROP / RENAME break a downgrade: allowed only with an explicit marker in the script
        for (f in scripts) {
            val texte = f.readText()
            val destructif = Regex("""(?i)\b(drop\s+(table|column)|rename\s+(table|column)|change\s+column)\b""").containsMatchIn(texte)
            assertFalse(destructif && "-- destructive" !in texte,
                "${f.name} supprime ou renomme : ajoutez « -- destructive » et décrivez la migration dans les notes de version")
        }
    }

    @Test
    fun `desactivation par variable d'environnement`() {
        assertTrue(MigrationsBase.actives(emptyMap()))
        assertTrue(MigrationsBase.actives(mapOf("THERMOBOX_DB_MIGRATE" to "true")))
        assertFalse(MigrationsBase.actives(mapOf("THERMOBOX_DB_MIGRATE" to "false")))
        assertFalse(MigrationsBase.actives(mapOf("THERMOBOX_DB_MIGRATE" to " OFF ")))
    }
}
