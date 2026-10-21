package ax.ibr.thermobox.persistence.migration

import org.flywaydb.core.Flyway
import java.util.logging.Logger

/**
 * Schema migrations (Flyway), run before JPA opens the database.
 *
 * Scripts live in `Persistence/src/main/resources/db/migration`, named `V<n>__<description>.sql`.
 * A script that was released must never change: Flyway checks their checksums and refuses to start
 * when one differs. Every schema change is a new script.
 *
 * - Empty database: every script runs.
 * - Database created before Flyway (EclipseLink `create`, up to 26.10): it is marked at version 0,
 *   then every script runs; they only use `CREATE TABLE IF NOT EXISTS`, so existing tables are kept.
 * - Database already migrated by a newer version (downgrade): the unknown scripts are tolerated
 *   (`*:future`) instead of blocking startup.
 *
 * REST and JSF may start together: Flyway takes a MySQL lock, the second one waits.
 */
object MigrationsBase {

    const val EMPLACEMENT = "classpath:db/migration"
    const val TABLE_HISTORIQUE = "flyway_schema_history"

    private val log = Logger.getLogger(MigrationsBase::class.java.name)

    /** `THERMOBOX_DB_MIGRATE=false` leaves the schema to the administrator. */
    fun actives(env: Map<String, String> = System.getenv()): Boolean =
        env["THERMOBOX_DB_MIGRATE"]?.trim()?.lowercase() !in setOf("false", "0", "non", "no", "off")

    fun migrer(url: String, utilisateur: String, motDePasse: String) {
        val resultat = Flyway.configure(MigrationsBase::class.java.classLoader)
            .dataSource(url, utilisateur, motDePasse)
            .locations(EMPLACEMENT)
            .table(TABLE_HISTORIQUE)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .baselineDescription("Base créée avant les migrations (EclipseLink)")
            .ignoreMigrationPatterns("*:future")
            // Never drop the production schema by mistake
            .cleanDisabled(true)
            .load()
            .migrate()
        if (resultat.migrationsExecuted > 0) {
            log.info("Schéma de la base : ${resultat.migrationsExecuted} migration(s) appliquée(s), " +
                "version ${resultat.initialSchemaVersion ?: "vide"} → ${resultat.targetSchemaVersion}")
        } else {
            log.info("Schéma de la base à jour (version ${resultat.targetSchemaVersion ?: resultat.initialSchemaVersion})")
        }
    }
}
