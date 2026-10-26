import ax.ibr.thermobox.business.implementations.RoomServiceImpl;
import ax.ibr.thermobox.business.implementations.RoomTempAttrServiceImpl;
import ax.ibr.thermobox.business.implementations.TemperatureServiceImpl;
import ax.ibr.thermobox.business.implementations.UserServiceImpl;
import ax.ibr.thermobox.common.entities.Room;
import ax.ibr.thermobox.common.entities.RoomTempAttr;
import ax.ibr.thermobox.common.entities.Temperature;
import ax.ibr.thermobox.common.entities.User;
import ax.ibr.thermobox.common.entities.UserType;
import ax.ibr.thermobox.persistence.dataservices.RoomDataService;
import ax.ibr.thermobox.persistence.dataservices.RoomTempAttrDataService;
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
    void delegatesGetByUsernameToDataService() {
        User user = mock(User.class);

        when(dataService.getByUsername("ib")).thenReturn(user);

        assertEquals(user, new UserServiceImpl(dataService).getByUsername("ib"));
    }

    @Test
    void delegatesGetByTypeToDataService() {
        User user1 = mock(User.class);
        User user2 = mock(User.class);

        List<User> users = List.of();

        when(dataService.getByType(UserType.ADMIN)).thenReturn(users);

        assertEquals(users, new UserServiceImpl(dataService).getByType(UserType.ADMIN));
    }

    @Test
    void delegatesAddUpdateAndRemoveToDataService() {
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


class RoomServiceImplTest {

    private RoomDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(RoomDataService.class);
    }

    @Test
    void delegatesGetByNameToDataService() {
        Room room = mock(Room.class);

        when(dataService.getByName("Room101")).thenReturn(room);

        assertEquals(room, new RoomServiceImpl(dataService).getByName("Room101"));
    }

    @Test
    void delegatesGetAllToDataService() {
        Room room = mock(Room.class);
        List<Room> rooms = java.util.List.of(room);

        when(dataService.getAll()).thenReturn(rooms);

        assertEquals(rooms, new RoomServiceImpl(dataService).getAll());
    }
}


class TemperatureServiceImplTest {

    private TemperatureDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(TemperatureDataService.class);
    }

    @Test
    void delegatesGetByIdToDataService() {
        Temperature temperature = mock(Temperature.class);

        when(dataService.getById(5L)).thenReturn(temperature);

        assertEquals(
                temperature,
                new TemperatureServiceImpl(dataService).getById(5L)
        );
    }

    @Test
    void delegatesAddToDataService() {
        Temperature temperature = mock(Temperature.class);

        new TemperatureServiceImpl(dataService).add(temperature);

        verify(dataService).add(temperature);
    }
}


class RoomTempAttrServiceImplTest {

    private RoomTempAttrDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = mock(RoomTempAttrDataService.class);
    }

    @Test
    void delegatesGetByRoomIdToDataService() {
        RoomTempAttr attr = mock(RoomTempAttr.class);

        when(dataService.getByRoom(3)).thenReturn(Collections.singletonList(attr));

        assertEquals(
                attr,
                new RoomTempAttrServiceImpl(dataService).getByRoom(3)
        );
    }

    @Test
    void delegatesGetByRoomObjectToDataService() {
        Room room = mock(Room.class);
        RoomTempAttr attr = mock(RoomTempAttr.class);

        when(dataService.getByRoom(room)).thenReturn(Collections.singletonList(attr));

        assertEquals(
                attr,
                new RoomTempAttrServiceImpl(dataService).getByRoom(room)
        );
    }
}
