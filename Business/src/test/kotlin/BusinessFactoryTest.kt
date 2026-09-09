package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BusinessFactoryTest {

    private lateinit var factory: BusinessFactory

    @BeforeEach
    fun setUp() {
        // Les XxxServiceImpl instancient PersistenceFactory() en dur dans leur
        // constructeur : on mocke PersistenceFactory pour que BusinessFactory
        // reste testable sans base de données réelle.
        mockkConstructor(PersistenceFactory::class)
        every { anyConstructed<PersistenceFactory>().getUserDataService() } returns mockk(relaxed = true)
        every { anyConstructed<PersistenceFactory>().getSalleDataService() } returns mockk(relaxed = true)
        every { anyConstructed<PersistenceFactory>().getTemperatureDataService() } returns mockk(relaxed = true)
        every { anyConstructed<PersistenceFactory>().getSalleTempAttrDataService() } returns mockk(relaxed = true)

        factory = BusinessFactory()
    }

    @AfterEach
    fun tearDown() = unmockkConstructor(PersistenceFactory::class)

    @Test
    fun `getUserService renvoie toujours la meme instance`() {
        assertSame(factory.getUserService(), factory.getUserService())
    }

    @Test
    fun `getSalleService renvoie toujours la meme instance`() {
        assertSame(factory.getSalleService(), factory.getSalleService())
    }

    @Test
    fun `getTemperatureService renvoie toujours la meme instance`() {
        assertSame(factory.getTemperatureService(), factory.getTemperatureService())
    }

    @Test
    fun `getSalleTempAttrService renvoie toujours la meme instance`() {
        assertSame(factory.getSalleTempAttrService(), factory.getSalleTempAttrService())
    }
}
