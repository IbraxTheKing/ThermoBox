package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Acces
import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.jpa.AccesDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.FavoriDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleImageDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleSalleAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleTempAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.TemperatureDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.UserDataServiceJPAImpl
import ax.ibr.thermobox.persistence.backup.ResultatSauvegarde
import ax.ibr.thermobox.persistence.backup.SauvegardeBase
import ax.ibr.thermobox.persistence.migration.MigrationsBase
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import java.nio.file.Path

object PersistenceFactory {

    private const val JDBC: Boolean = false
    private const val PU: String = "thermoboxPU"
    private const val DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/thermobox"

    // Schema migrations first: JPA only maps the tables, it never creates or alters them.
    private val emfHolder = lazy {
        if (MigrationsBase.actives()) {
            MigrationsBase.migrer(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD"))
        }
        Persistence.createEntityManagerFactory(PU, databaseProperties())
    }
    private val emf: EntityManagerFactory by emfHolder

    private val userService: UserDataService by lazy {
        if (JDBC) TODO("JDBC version") else UserDataServiceJPAImpl(emf, User::class.java)
    }

    private val salleService: SalleDataService by lazy {
        if (JDBC) TODO("JDBC version") else SalleDataServiceJPAImpl(emf, Salle::class.java)
    }

    private val salleTempAttrService: SalleTempAttrDataService by lazy {
        if (JDBC) TODO("JDBC version") else SalleTempAttrDataServiceJPAImpl(emf, SalleTempAttr::class.java)
    }

    private val temperatureService: TemperatureDataService by lazy {
        if (JDBC) TODO("JDBC version") else TemperatureDataServiceJPAImpl(emf, Temperature::class.java)
    }

    private val salleSalleAttrService: SalleSalleAttrDataService by lazy {
        if (JDBC) TODO("JDBC version") else SalleSalleAttrDataServiceJPAImpl(emf, SalleSalleAttr::class.java)
    }

    private val favoriService: FavoriDataService by lazy {
        if (JDBC) TODO("JDBC version") else FavoriDataServiceJPAImpl(emf, Favori::class.java)
    }

    private val salleImageService: SalleImageDataService by lazy {
        if (JDBC) TODO("JDBC version") else SalleImageDataServiceJPAImpl(emf, SalleImage::class.java)
    }

    private val accesService: AccesDataService by lazy {
        if (JDBC) TODO("JDBC version") else AccesDataServiceJPAImpl(emf, Acces::class.java)
    }

    fun getAccesDataService(): AccesDataService = accesService

    fun getUserDataService(): UserDataService = userService

    fun getSalleDataService(): SalleDataService = salleService

    fun getSalleTempAttrDataService(): SalleTempAttrDataService = salleTempAttrService

    fun getTemperatureDataService(): TemperatureDataService = temperatureService

    fun getSalleSalleAttrDataService(): SalleSalleAttrDataService = salleSalleAttrService

    fun getFavoriDataService(): FavoriDataService = favoriService

    fun getSalleImageDataService(): SalleImageDataService = salleImageService

    fun close() {
        if (emfHolder.isInitialized() && emf.isOpen) emf.close()
    }

    /** Full SQL dump of the database (gzip), with the same connection settings as JPA. */
    fun sauvegarderBase(fichier: Path): ResultatSauvegarde =
        SauvegardeBase(databaseUrl(), requireEnv("THERMOBOX_DB_USER"), requireEnv("THERMOBOX_DB_PASSWORD")).ecrire(fichier)

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
