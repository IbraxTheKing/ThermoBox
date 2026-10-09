package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.jpa.SalleDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleTempAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.TemperatureDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.UserDataServiceJPAImpl
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence

object PersistenceFactory {

    private const val JDBC: Boolean = false
    private const val PU: String = "thermoboxPU"
    private const val DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/thermobox"

    private val emfHolder = lazy { Persistence.createEntityManagerFactory(PU, databaseProperties()) }
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
        if (JDBC) TODO("JDBC version") else TODO("JPA version")
    }

    fun getUserDataService(): UserDataService = userService

    fun getSalleDataService(): SalleDataService = salleService

    fun getSalleTempAttrDataService(): SalleTempAttrDataService = salleTempAttrService

    fun getTemperatureDataService(): TemperatureDataService = temperatureService

    fun getSalleSalleAttrDataService(): SalleSalleAttrDataService = salleSalleAttrService

    fun close() {
        if (emfHolder.isInitialized() && emf.isOpen) emf.close()
    }

    /**
     * JDBC settings read from the environment, so no credentials live in persistence.xml:
     * `THERMOBOX_DB_URL`, `THERMOBOX_DB_USER`, `THERMOBOX_DB_PASSWORD`.
     */
    private fun databaseProperties(): Map<String, String> = mapOf(
        "jakarta.persistence.jdbc.url" to (System.getenv("THERMOBOX_DB_URL") ?: DEFAULT_DB_URL),
        "jakarta.persistence.jdbc.user" to requireEnv("THERMOBOX_DB_USER"),
        "jakarta.persistence.jdbc.password" to requireEnv("THERMOBOX_DB_PASSWORD")
    )

    private fun requireEnv(name: String): String =
        System.getenv(name) ?: error("Environment variable $name is not set")
}
