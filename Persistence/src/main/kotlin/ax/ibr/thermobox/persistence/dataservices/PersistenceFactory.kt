package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.jpa.SalleDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.SalleTempAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.TemperatureDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.UserDataServiceJPAImpl
import jakarta.persistence.EntityManager
import jakarta.persistence.Persistence
import kotlin.jvm.java

class PersistenceFactory {

    private lateinit var temperatureService: TemperatureDataService
    private lateinit var salleService: SalleDataService
    private lateinit var userService: UserDataService
    private lateinit var salleTempAttrService: SalleTempAttrDataService

    private val JDBC: Boolean = false
    private val PU: String = ""

    private val entityManager: EntityManager by lazy {
        Persistence.createEntityManagerFactory(PU).createEntityManager()
    }

    fun getUserDataService() : UserDataService {
        if (!::userService.isInitialized) {
            if (!JDBC) {
                userService = UserDataServiceJPAImpl(PU, entityManager, User::class.java)
            }
            // TODO: Faire la version JDBC
        }
        return userService
    }

    fun getSalleDataService() : SalleDataService {
        if (!::salleService.isInitialized) {
            if (!JDBC) {
                salleService = SalleDataServiceJPAImpl(PU, entityManager, Salle::class.java)
            }
            // TODO: Faire la version JDBC
        }
        return salleService
    }

    fun getSalleTempAttrDataService() : SalleTempAttrDataService {
        if (!::salleTempAttrService.isInitialized) {
            if (!JDBC) {
                salleTempAttrService = SalleTempAttrDataServiceJPAImpl(PU, entityManager, SalleTempAttr::class.java)
            }
            //TODO: Faire la version JDBC
        }
        return salleTempAttrService
    }

    fun getTemperatureDataService() : TemperatureDataService {
        if (!::temperatureService.isInitialized) {
            if (!JDBC) {
                temperatureService = TemperatureDataServiceJPAImpl(PU, entityManager, Temperature::class.java)
            }
            // TODO: Faire la version JDBC
        }
        return temperatureService
    }

}