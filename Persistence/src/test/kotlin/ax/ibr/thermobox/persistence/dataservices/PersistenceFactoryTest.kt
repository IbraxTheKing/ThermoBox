package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.persistence.jdbc.RoomDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.RoomTempAttrDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.TemperatureDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jdbc.UserDataServiceJdbcImpl
import ax.ibr.thermobox.persistence.jpa.RoomDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.RoomTempAttrDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.TemperatureDataServiceJPAImpl
import ax.ibr.thermobox.persistence.jpa.UserDataServiceJPAImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The factory gives the implementation of THERMOBOX_DB_ACCESS (JDBC unless it says jpa). Needs THERMOBOX_DB_*. */
class PersistenceFactoryTest {

    private val factory = PersistenceFactory
    private val jdbc = PersistenceFactory.accessMode() == PersistenceFactory.AccessMode.JDBC

    private fun check(service: Any, jdbcType: Class<*>, jpaType: Class<*>) {
        val expected = if (jdbc) jdbcType else jpaType
        assertTrue(expected.isInstance(service), "Expected ${expected.simpleName} but got ${service.javaClass.simpleName}")
    }

    @Test
    fun `room service of the chosen access`() =
        check(factory.getRoomDataService(), RoomDataServiceJdbcImpl::class.java, RoomDataServiceJPAImpl::class.java)

    @Test
    fun `room temperature service of the chosen access`() =
        check(factory.getRoomTempAttrDataService(), RoomTempAttrDataServiceJdbcImpl::class.java, RoomTempAttrDataServiceJPAImpl::class.java)

    @Test
    fun `temperature service of the chosen access`() =
        check(factory.getTemperatureDataService(), TemperatureDataServiceJdbcImpl::class.java, TemperatureDataServiceJPAImpl::class.java)

    @Test
    fun `user service of the chosen access`() =
        check(factory.getUserDataService(), UserDataServiceJdbcImpl::class.java, UserDataServiceJPAImpl::class.java)
}
