
package ax.ibr.thermobox.persistence.dataservices

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ax.ibr.thermobox.persistence.jpa.*

class PersistenceFactoryTest {

    private val factory = PersistenceFactory()

    @Test
    fun `test getSalleDataService returns SalleDataServiceJPAImpl`() {
        val service = factory.getSalleDataService()
        assertNotNull(service, "The SalleDataService should not be null")
        assertTrue(service is SalleDataServiceJPAImpl, "Expected SalleDataServiceJPAImpl but got ${service?.javaClass?.simpleName}")
    }

    @Test
    fun `test getSalleTempAttrDataService returns SalleTempAttrDataServiceJPAImpl`() {
        val service = factory.getSalleTempAttrDataService()
        assertNotNull(service, "The SalleTempAttrDataService should not be null")
        assertTrue(service is SalleTempAttrDataServiceJPAImpl, "Expected SalleTempAttrDataServiceJPAImpl but got ${service?.javaClass?.simpleName}")
    }

    @Test
    fun `test getTemperatureDataService returns TemperatureDataServiceJPAImpl`() {
        val service = factory.getTemperatureDataService()
        assertNotNull(service, "The TemperatureDataService should not be null")
        assertTrue(service is TemperatureDataServiceJPAImpl, "Expected TemperatureDataServiceJPAImpl but got ${service?.javaClass?.simpleName}")
    }

    @Test
    fun `test getUserDataService returns UserDataServiceJPAImpl`() {
        val service = factory.getUserDataService()
        assertNotNull(service, "The UserDataService should not be null")
        assertTrue(service is UserDataServiceJPAImpl, "Expected UserDataServiceJPAImpl but got ${service?.javaClass?.simpleName}")
    }
}