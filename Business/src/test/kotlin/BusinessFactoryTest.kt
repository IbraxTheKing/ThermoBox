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
        // PersistenceFactory est un object : on le mocke pour tester BusinessFactory sans base réelle.
        mockkObject(PersistenceFactory)
        every { PersistenceFactory.getUserDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getSalleDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getTemperatureDataService() } returns mockk(relaxed = true)
        every { PersistenceFactory.getSalleTempAttrDataService() } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() = unmockkObject(PersistenceFactory)

    @Test
    fun `getUserService renvoie toujours la meme instance`() {
        assertSame(BusinessFactory.getUserService(), BusinessFactory.getUserService())
    }

    @Test
    fun `getSalleService renvoie toujours la meme instance`() {
        assertSame(BusinessFactory.getSalleService(), BusinessFactory.getSalleService())
    }

    @Test
    fun `getTemperatureService renvoie toujours la meme instance`() {
        assertSame(BusinessFactory.getTemperatureService(), BusinessFactory.getTemperatureService())
    }

    @Test
    fun `getSalleTempAttrService renvoie toujours la meme instance`() {
        assertSame(BusinessFactory.getSalleTempAttrService(), BusinessFactory.getSalleTempAttrService())
    }
}
