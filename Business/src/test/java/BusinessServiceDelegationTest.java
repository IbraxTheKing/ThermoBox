import ax.ibr.thermobox.business.implementations.SalleServiceImpl;
import ax.ibr.thermobox.business.implementations.SalleTempAttrServiceImpl;
import ax.ibr.thermobox.business.implementations.TemperatureServiceImpl;
import ax.ibr.thermobox.business.implementations.UserServiceImpl;
import ax.ibr.thermobox.common.entities.Salle;
import ax.ibr.thermobox.common.entities.SalleTempAttr;
import ax.ibr.thermobox.common.entities.Temperature;
import ax.ibr.thermobox.common.entities.User;
import ax.ibr.thermobox.common.entities.UserType;
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory;
import ax.ibr.thermobox.persistence.dataservices.SalleDataService;
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService;
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService;
import ax.ibr.thermobox.persistence.dataservices.UserDataService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserDataService dataService;
    private MockedConstruction<PersistenceFactory> persistenceFactoryMock;

    @BeforeEach
    void setUp() {
        dataService = mock(UserDataService.class);

        persistenceFactoryMock = mockConstruction(
                PersistenceFactory.class,
                (mock, context) -> when(mock.getUserDataService()).thenReturn(dataService)
        );
    }

    @AfterEach
    void tearDown() {
        persistenceFactoryMock.close();
    }

    @Test
    void delegueGetByUsernameAuDataService() {
        User user = mock(User.class);

        when(dataService.getByUsername("ibrahim")).thenReturn(user);

        assertEquals(user, new UserServiceImpl().getByUsername("ibrahim"));
    }

    @Test
    void delegueGetByTypeAuDataService() {
        User user1 = mock(User.class);
        User user2 = mock(User.class);

        List<User> users = List.of();

        when(dataService.getByType(UserType.ADMIN)).thenReturn(users);

        assertEquals(users, new UserServiceImpl().getByType(UserType.ADMIN));
    }

    @Test
    void delegueAddUpdateEtRemoveAuDataService() {
        User user = mock(User.class);
        UserServiceImpl service = new UserServiceImpl();

        service.add(user);
        service.update(user);
        service.remove(user);

        verify(dataService).add(user);
        verify(dataService).update(user);
        verify(dataService).remove(user);
    }
}


class SalleServiceImplTest {

    private SalleDataService dataService;
    private MockedConstruction<PersistenceFactory> persistenceFactoryMock;

    @BeforeEach
    void setUp() {
        dataService = mock(SalleDataService.class);

        persistenceFactoryMock = mockConstruction(
                PersistenceFactory.class,
                (mock, context) -> when(mock.getSalleDataService()).thenReturn(dataService)
        );
    }

    @AfterEach
    void tearDown() {
        persistenceFactoryMock.close();
    }

    @Test
    void delegueGetByNameAuDataService() {
        Salle salle = mock(Salle.class);

        when(dataService.getByName("Salle101")).thenReturn(salle);

        assertEquals(salle, new SalleServiceImpl().getByName("Salle101"));
    }

    @Test
    void delegueGetAllAuDataService() {
        Salle salle = mock(Salle.class);
        List<Salle> salles = java.util.List.of(salle);

        when(dataService.getAll()).thenReturn(salles);

        assertEquals(salles, new SalleServiceImpl().getAll());
    }
}


class TemperatureServiceImplTest {

    private TemperatureDataService dataService;
    private MockedConstruction<PersistenceFactory> persistenceFactoryMock;

    @BeforeEach
    void setUp() {
        dataService = mock(TemperatureDataService.class);

        persistenceFactoryMock = mockConstruction(
                PersistenceFactory.class,
                (mock, context) -> when(mock.getTemperatureDataService()).thenReturn(dataService)
        );
    }

    @AfterEach
    void tearDown() {
        persistenceFactoryMock.close();
    }

    @Test
    void delegueGetByIdAuDataService() {
        Temperature temperature = mock(Temperature.class);

        when(dataService.getById(5L)).thenReturn(temperature);

        assertEquals(
                temperature,
                new TemperatureServiceImpl().getById(5L)
        );
    }

    @Test
    void delegueAddAuDataService() {
        Temperature temperature = mock(Temperature.class);

        new TemperatureServiceImpl().add(temperature);

        verify(dataService).add(temperature);
    }
}


class SalleTempAttrServiceImplTest {

    private SalleTempAttrDataService dataService;
    private MockedConstruction<PersistenceFactory> persistenceFactoryMock;

    @BeforeEach
    void setUp() {
        dataService = mock(SalleTempAttrDataService.class);

        persistenceFactoryMock = mockConstruction(
                PersistenceFactory.class,
                (mock, context) -> when(mock.getSalleTempAttrDataService()).thenReturn(dataService)
        );
    }

    @AfterEach
    void tearDown() {
        persistenceFactoryMock.close();
    }

    @Test
    void delegueGetBySalleIdAuDataService() {
        SalleTempAttr attr = mock(SalleTempAttr.class);

        when(dataService.getBySalle(3)).thenReturn(attr);

        assertEquals(
                attr,
                new SalleTempAttrServiceImpl().getBySalle(3)
        );
    }

    @Test
    void delegueGetBySalleObjetAuDataService() {
        Salle salle = mock(Salle.class);
        SalleTempAttr attr = mock(SalleTempAttr.class);

        when(dataService.getBySalle(salle)).thenReturn(attr);

        assertEquals(
                attr,
                new SalleTempAttrServiceImpl().getBySalle(salle)
        );
    }
}
