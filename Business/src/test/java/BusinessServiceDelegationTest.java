import ax.ibr.thermobox.business.implementations.SalleServiceImpl;
import ax.ibr.thermobox.business.implementations.SalleTempAttrServiceImpl;
import ax.ibr.thermobox.business.implementations.TemperatureServiceImpl;
import ax.ibr.thermobox.business.implementations.UserServiceImpl;
import ax.ibr.thermobox.common.entities.Salle;
import ax.ibr.thermobox.common.entities.SalleTempAttr;
import ax.ibr.thermobox.common.entities.Temperature;
import ax.ibr.thermobox.common.entities.User;
import ax.ibr.thermobox.common.entities.UserType;
import ax.ibr.thermobox.persistence.dataservices.SalleDataService;
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService;
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService;
import ax.ibr.thermobox.persistence.dataservices.UserDataService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(UserDataService.class);
    }

    @Test
    void delegueGetByUsernameAuDataService() {
        User user = mock(User.class);

        when(dataService.getByUsername("ib")).thenReturn(user);

        assertEquals(user, new UserServiceImpl(dataService).getByUsername("ib"));
    }

    @Test
    void delegueGetByTypeAuDataService() {
        User user1 = mock(User.class);
        User user2 = mock(User.class);

        List<User> users = List.of();

        when(dataService.getByType(UserType.ADMIN)).thenReturn(users);

        assertEquals(users, new UserServiceImpl(dataService).getByType(UserType.ADMIN));
    }

    @Test
    void delegueAddUpdateEtRemoveAuDataService() {
        User user = mock(User.class);
        UserServiceImpl service = new UserServiceImpl(dataService);

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

    @BeforeEach
    void setUp() {
        dataService = mock(SalleDataService.class);
    }

    @Test
    void delegueGetByNameAuDataService() {
        Salle salle = mock(Salle.class);

        when(dataService.getByName("Salle101")).thenReturn(salle);

        assertEquals(salle, new SalleServiceImpl(dataService).getByName("Salle101"));
    }

    @Test
    void delegueGetAllAuDataService() {
        Salle salle = mock(Salle.class);
        List<Salle> salles = java.util.List.of(salle);

        when(dataService.getAll()).thenReturn(salles);

        assertEquals(salles, new SalleServiceImpl(dataService).getAll());
    }
}


class TemperatureServiceImplTest {

    private TemperatureDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(TemperatureDataService.class);
    }

    @Test
    void delegueGetByIdAuDataService() {
        Temperature temperature = mock(Temperature.class);

        when(dataService.getById(5L)).thenReturn(temperature);

        assertEquals(
                temperature,
                new TemperatureServiceImpl(dataService).getById(5L)
        );
    }

    @Test
    void delegueAddAuDataService() {
        Temperature temperature = mock(Temperature.class);

        new TemperatureServiceImpl(dataService).add(temperature);

        verify(dataService).add(temperature);
    }
}


class SalleTempAttrServiceImplTest {

    private SalleTempAttrDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(SalleTempAttrDataService.class);
    }

    @Test
    void delegueGetBySalleIdAuDataService() {
        SalleTempAttr attr = mock(SalleTempAttr.class);

        when(dataService.getBySalle(3)).thenReturn(Collections.singletonList(attr));

        assertEquals(
                attr,
                new SalleTempAttrServiceImpl(dataService).getBySalle(3)
        );
    }

    @Test
    void delegueGetBySalleObjetAuDataService() {
        Salle salle = mock(Salle.class);
        SalleTempAttr attr = mock(SalleTempAttr.class);

        when(dataService.getBySalle(salle)).thenReturn(Collections.singletonList(attr));

        assertEquals(
                attr,
                new SalleTempAttrServiceImpl(dataService).getBySalle(salle)
        );
    }
}
