package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BusinessFactoryTest {

    @BeforeEach
    fun setUp() {
        // PersistenceFactory is an object: it is mocked to test BusinessFactory without a real database.
        mockkObject(PersistenceFactory)
        every { PersistenceFactory.getUserDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getRoomDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getTemperatureDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getRoomTempAttrDataService() } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() = unmockkObject(PersistenceFactory)

    @Test
    fun `getUserService always returns the same instance`() {
        assertSame(BusinessFactory.getUserService(), BusinessFactory.getUserService())
    }

    @Test
    fun `getRoomService always returns the same instance`() {
        assertSame(BusinessFactory.getRoomService(), BusinessFactory.getRoomService())
    }

    @Test
    fun `getTemperatureService always returns the same instance`() {
        assertSame(BusinessFactory.getTemperatureService(), BusinessFactory.getTemperatureService())
    }

    @Test
    fun `getRoomTempAttrService always returns the same instance`() {
        assertSame(BusinessFactory.getRoomTempAttrService(), BusinessFactory.getRoomTempAttrService())
    }
}
