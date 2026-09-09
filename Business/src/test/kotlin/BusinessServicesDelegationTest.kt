import ax.ibr.thermobox.business.implementations.SalleServiceImpl
import ax.ibr.thermobox.business.implementations.SalleTempAttrServiceImpl
import ax.ibr.thermobox.business.implementations.TemperatureServiceImpl
import ax.ibr.thermobox.business.implementations.UserServiceImpl
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.thermobox.persistence.dataservices.SalleDataService
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService
import ax.ibr.thermobox.persistence.dataservices.UserDataService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserServiceImplTest {

    private lateinit var dataService: UserDataService

    @BeforeEach
    fun setUp() {
        dataService = mockk(relaxed = true)
        mockkConstructor(PersistenceFactory::class)
        every { anyConstructed<PersistenceFactory>().getUserDataService() } returns dataService
    }

    @AfterEach
    fun tearDown() = unmockkConstructor(PersistenceFactory::class)

    @Test
    fun `delegue getByUsername au data service`() {
        val user = mockk<User>()
        every { dataService.getByUsername("ibrahim") } returns user

        assertEquals(user, UserServiceImpl().getByUsername("ibrahim"))
    }

    @Test
    fun `delegue getByType au data service`() {
        val users = listOf(mockk<User>(), mockk<User>())
        every { dataService.getByType(UserType.ADMIN) } returns users

        assertEquals(users, UserServiceImpl().getByType(UserType.ADMIN))
    }

    @Test
    fun `delegue add, update et remove au data service`() {
        val user = mockk<User>()
        val service = UserServiceImpl()

        service.add(user)
        service.update(user)
        service.remove(user)

        verify { dataService.add(user) }
        verify { dataService.update(user) }
        verify { dataService.remove(user) }
    }
}

class SalleServiceImplTest {

    private lateinit var dataService: SalleDataService

    @BeforeEach
    fun setUp() {
        dataService = mockk(relaxed = true)
        mockkConstructor(PersistenceFactory::class)
        every { anyConstructed<PersistenceFactory>().getSalleDataService() } returns dataService
    }

    @AfterEach
    fun tearDown() = unmockkConstructor(PersistenceFactory::class)

    @Test
    fun `delegue getByName au data service`() {
        val salle = mockk<Salle>()
        every { dataService.getByName("Salle101") } returns salle

        assertEquals(salle, SalleServiceImpl().getByName("Salle101"))
    }

    @Test
    fun `delegue getAll au data service`() {
        val salles = listOf(mockk<Salle>())
        every { dataService.getAll() } returns salles

        assertEquals(salles, SalleServiceImpl().getAll())
    }
}

class TemperatureServiceImplTest {

    private lateinit var dataService: TemperatureDataService

    @BeforeEach
    fun setUp() {
        dataService = mockk(relaxed = true)
        mockkConstructor(PersistenceFactory::class)
        every { anyConstructed<PersistenceFactory>().getTemperatureDataService() } returns dataService
    }

    @AfterEach
    fun tearDown() = unmockkConstructor(PersistenceFactory::class)

    @Test
    fun `delegue getById au data service`() {
        val temperature = mockk<Temperature>()
        every { dataService.getById(5L) } returns temperature

        assertEquals(temperature, TemperatureServiceImpl().getById(5L))
    }

    @Test
    fun `delegue add au data service`() {
        val temperature = mockk<Temperature>()

        TemperatureServiceImpl().add(temperature)

        verify { dataService.add(temperature) }
    }
}

class SalleTempAttrServiceImplTest {

    private lateinit var dataService: SalleTempAttrDataService

    @BeforeEach
    fun setUp() {
        dataService = mockk(relaxed = true)
        mockkConstructor(PersistenceFactory::class)
        every { anyConstructed<PersistenceFactory>().getSalleTempAttrDataService() } returns dataService
    }

    @AfterEach
    fun tearDown() = unmockkConstructor(PersistenceFactory::class)

    @Test
    fun `delegue getBySalle(id) au data service`() {
        val attr = mockk<SalleTempAttr>()
        every { dataService.getBySalle(3) } returns attr

        assertEquals(attr, SalleTempAttrServiceImpl().getBySalle(3))
    }

    @Test
    fun `delegue getBySalle(salle) au data service`() {
        val salle = mockk<Salle>()
        val attr = mockk<SalleTempAttr>()
        every { dataService.getBySalle(salle) } returns attr

        assertEquals(attr, SalleTempAttrServiceImpl().getBySalle(salle))
    }
}
