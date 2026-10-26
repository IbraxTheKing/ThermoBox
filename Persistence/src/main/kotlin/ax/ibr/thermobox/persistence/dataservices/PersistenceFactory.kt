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
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import java.nio.file.Path

object PersistenceFactory {

    private const val JDBC: Boolean = false
    private const val PU: String = "thermoboxPU"
    private const val DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/thermobox"

    // Schema migrations first: JPA only maps the tables, it never creates or alters them.
    private val emfHolder = lazy {
        if (DatabaseMigrations.enabled()) {
            DatabaseMigrations.migrate(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD"))
        }
        Persistence.createEntityManagerFactory(PU, databaseProperties())
    }
    private val emf: EntityManagerFactory by emfHolder

    private val userService: UserDataService by lazy {
        if (JDBC) TODO("JDBC version") else UserDataServiceJPAImpl(emf, User::class.java)
    }

    private val roomService: RoomDataService by lazy {
        if (JDBC) TODO("JDBC version") else RoomDataServiceJPAImpl(emf, Room::class.java)
    }

    private val roomTempAttrService: RoomTempAttrDataService by lazy {
        if (JDBC) TODO("JDBC version") else RoomTempAttrDataServiceJPAImpl(emf, RoomTempAttr::class.java)
    }

    private val temperatureService: TemperatureDataService by lazy {
        if (JDBC) TODO("JDBC version") else TemperatureDataServiceJPAImpl(emf, Temperature::class.java)
    }

    private val roomRoomAttrService: RoomRoomAttrDataService by lazy {
        if (JDBC) TODO("JDBC version") else RoomRoomAttrDataServiceJPAImpl(emf, RoomRoomAttr::class.java)
    }

    private val favoriteService: FavoriteDataService by lazy {
        if (JDBC) TODO("JDBC version") else FavoriteDataServiceJPAImpl(emf, Favorite::class.java)
    }

    private val roomImageService: RoomImageDataService by lazy {
        if (JDBC) TODO("JDBC version") else RoomImageDataServiceJPAImpl(emf, RoomImage::class.java)
    }

    private val accessService: AccessDataService by lazy {
        if (JDBC) TODO("JDBC version") else AccessDataServiceJPAImpl(emf, Access::class.java)
    }

    fun getAccessDataService(): AccessDataService = accessService

    private val userGroupService: UserGroupDataService by lazy { UserGroupDataServiceJPAImpl(emf, UserGroup::class.java) }

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
        "jakarta.persistence.jdbc.password" to requireEnv("THERMOBOX_DB_PASSWORD")
    )

    private fun requireEnv(name: String): String =
        System.getenv(name) ?: error("Environment variable $name is not set")
}
