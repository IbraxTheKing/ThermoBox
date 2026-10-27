package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.persistence.jpa.UserGroupDataServiceJPAImpl
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.common.entities.RoomTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.jpa.AccessDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.FavoriteDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.RoomDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.RoomImageDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.RoomRoomAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.RoomTempAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.TemperatureDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.UserDataServiceJPAImpl
import ax.ibr.thermobox.persistence.backup.BackupResult
import ax.ibr.thermobox.persistence.backup.DatabaseBackup
import ax.ibr.thermobox.persistence.migration.DatabaseMigrations
import ax.ibr.thermobox.persistence.jdbc.AccessDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.FavoriteDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.JdbcDatabase
import ax.ibr.thermobox.persistence.jdbc.RoomDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.RoomImageDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.RoomRoomAttrDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.RoomTempAttrDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.TemperatureDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.UserDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.UserGroupDataServiceJdbcImpl
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import java.nio.file.Path

/**
 * The data services of the application, all on the database of `THERMOBOX_DB_URL`.
 *
 * Two implementations of every service, chosen by `THERMOBOX_DB_ACCESS`:
 * - `jdbc` (default since 26.12): plain SQL through JDBC (package `jdbc`), without JPA: every
 *   query is written out. Under load it answered 22 times more requests than JPA (770 a second
 *   against 35, loadtest/);
 * - `jpa`: JPA with EclipseLink (package `jpa`), the access of 26.11 and before, kept for a
 *   while as a way back.
 * Both read and write the same tables, so a server can switch at any restart.
 *
 * In both cases the Flyway migrations run first: the schema belongs to them.
 */
object PersistenceFactory {

    /** How the data services reach the database. */
    enum class AccessMode { JPA, JDBC }

    private const val PU: String = "thermoboxPU"
    private const val DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/thermobox"

    /**
     * `THERMOBOX_DB_ACCESS`: `jdbc` (default) or `jpa`.
     * @throws IllegalStateException for any other value, so a typo does not go unnoticed
     */
    fun accessMode(env: Map<String, String> = System.getenv()): AccessMode =
        when (val v = env["THERMOBOX_DB_ACCESS"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }) {
            null, "jdbc" -> AccessMode.JDBC
            "jpa" -> AccessMode.JPA
            else -> error("THERMOBOX_DB_ACCESS=$v is not valid: jdbc or jpa")
        }

    private val mode: AccessMode by lazy { accessMode() }

    private val migrated by lazy {
        if (DatabaseMigrations.enabled()) {
            DatabaseMigrations.migrate(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD"))
        }
    }

    // Schema migrations first: JPA and JDBC only use the tables, they never create or alter them.
    private val emfHolder = lazy {
        migrated
        Persistence.createEntityManagerFactory(PU, databaseProperties())
    }
    private val emf: EntityManagerFactory by emfHolder

    private val jdbcHolder = lazy {
        migrated
        JdbcDatabase(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD"), maxOpen = poolSize())
    }
    private val jdbc: JdbcDatabase by jdbcHolder

    private fun <T> pick(jpa: () -> T, jdbc: (JdbcDatabase) -> T): T = when (mode) {
        AccessMode.JPA -> jpa()
        AccessMode.JDBC -> jdbc(this.jdbc)
    }

    private val userService: UserDataService by lazy {
        pick({ UserDataServiceJPAImpl(emf, User::class.java) }, ::UserDataServiceJdbcImpl)
    }

    private val roomService: RoomDataService by lazy {
        pick({ RoomDataServiceJPAImpl(emf, Room::class.java) }, ::RoomDataServiceJdbcImpl)
    }

    private val roomTempAttrService: RoomTempAttrDataService by lazy {
        pick({ RoomTempAttrDataServiceJPAImpl(emf, RoomTempAttr::class.java) }, ::RoomTempAttrDataServiceJdbcImpl)
    }

    private val temperatureService: TemperatureDataService by lazy {
        pick({ TemperatureDataServiceJPAImpl(emf, Temperature::class.java) }, ::TemperatureDataServiceJdbcImpl)
    }

    private val roomRoomAttrService: RoomRoomAttrDataService by lazy {
        pick({ RoomRoomAttrDataServiceJPAImpl(emf, RoomRoomAttr::class.java) }, ::RoomRoomAttrDataServiceJdbcImpl)
    }

    private val favoriteService: FavoriteDataService by lazy {
        pick({ FavoriteDataServiceJPAImpl(emf, Favorite::class.java) }, ::FavoriteDataServiceJdbcImpl)
    }

    private val roomImageService: RoomImageDataService by lazy {
        pick({ RoomImageDataServiceJPAImpl(emf, RoomImage::class.java) }, ::RoomImageDataServiceJdbcImpl)
    }

    private val accessService: AccessDataService by lazy {
        pick({ AccessDataServiceJPAImpl(emf, Access::class.java) }, ::AccessDataServiceJdbcImpl)
    }

    private val userGroupService: UserGroupDataService by lazy {
        pick({ UserGroupDataServiceJPAImpl(emf, UserGroup::class.java) }, ::UserGroupDataServiceJdbcImpl)
    }

    fun getAccessDataService(): AccessDataService = accessService

    fun getUserGroupDataService(): UserGroupDataService = userGroupService

    fun getUserDataService(): UserDataService = userService

    fun getRoomDataService(): RoomDataService = roomService

    fun getRoomTempAttrDataService(): RoomTempAttrDataService = roomTempAttrService

    fun getTemperatureDataService(): TemperatureDataService = temperatureService

    fun getRoomRoomAttrDataService(): RoomRoomAttrDataService = roomRoomAttrService

    fun getFavoriteDataService(): FavoriteDataService = favoriteService

    fun getRoomImageDataService(): RoomImageDataService = roomImageService

    fun close() {
        if (emfHolder.isInitialized() && emf.isOpen) emf.close()
        if (jdbcHolder.isInitialized()) jdbc.close()
    }

    /** Full SQL dump of the database (gzip), with the same connection settings as JPA. */
    fun backupDatabase(file: Path): BackupResult =
        DatabaseBackup(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD")).write(file)

    private fun databaseUrl(): String = System.getenv("THERMOBOX_DB_URL") ?: DEFAULT_DB_URL

    /**
     * JDBC settings read from the environment, so no credentials live in persistence.xml:
     * `THERMOBOX_DB_URL`, `THERMOBOX_DB_USER`, `THERMOBOX_DB_PASSWORD`.
     */
    private fun databaseProperties(): Map<String, String> = mapOf(
        "jakarta.persistence.jdbc.url" to databaseUrl(),
        "jakarta.persistence.jdbc.user" to requireEnv("THERMOBOX_DB_USER"),
        "jakarta.persistence.jdbc.password" to requireEnv("THERMOBOX_DB_PASSWORD"),
        // Same ceiling as the JDBC access (EclipseLink: 32 by default)
        "eclipselink.connection-pool.default.max" to poolSize().toString(),
        "eclipselink.connection-pool.default.min" to "1",
        "eclipselink.connection-pool.default.initial" to "1",
    )

    /**
     * `THERMOBOX_DB_POOL_SIZE`: connections to the database at most at once (20 by default); the
     * other requests wait their turn. MySQL accepts 151 by default, for every server together.
     * @throws IllegalStateException for a value that is not a number from 1 to 500
     */
    fun poolSize(env: Map<String, String> = System.getenv()): Int {
        val raw = env["THERMOBOX_DB_POOL_SIZE"]?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_POOL_SIZE
        return raw.toIntOrNull()?.takeIf { it in 1..500 } ?: error("THERMOBOX_DB_POOL_SIZE=$raw is not valid: a number from 1 to 500")
    }

    private const val DEFAULT_POOL_SIZE = 20

    private fun requireEnv(name: String): String =
        System.getenv(name) ?: error("Environment variable $name is not set")
}
