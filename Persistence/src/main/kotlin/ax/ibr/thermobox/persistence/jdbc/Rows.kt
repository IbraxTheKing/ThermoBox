package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Building
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Floor
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.common.entities.UserType
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * How the entities are stored, as the JPA mapping (Common/entities) and the Flyway scripts define
 * it, and how a row becomes an entity again.
 *
 * The columns of a joined entity are selected under a prefix (`r_id`, `r_name`…), so one query
 * can return a favorite with its user and its room. [roomColumns] writes that select list and
 * [room] reads it back.
 */
internal object Rows {

    // --- room: Room, Building, Floor and Device share the table, told apart by DTYPE ---

    fun roomColumns(alias: String, prefix: String = alias) =
        "$alias.id AS ${prefix}_id, $alias.DTYPE AS ${prefix}_dtype, $alias.name AS ${prefix}_name"

    /** The room of the row, null when the (outer) join found none. */
    fun room(rs: ResultSet, prefix: String): Room? {
        val id = rs.longOrNull("${prefix}_id") ?: return null
        val name = rs.getString("${prefix}_name")
        val room = when (rs.getString("${prefix}_dtype")) {
            "Building" -> Building(name)
            "Floor" -> Floor(name)
            "Device" -> Device(name)
            else -> Room(name)
        }
        room.id = id
        return room
    }

    /** The DTYPE written for [room]'s class (same values as the JPA @DiscriminatorValue). */
    fun roomType(room: Room): String = roomType(room.javaClass)

    fun roomType(type: Class<out Room>): String = when (type) {
        Building::class.java -> "Building"
        Floor::class.java -> "Floor"
        Device::class.java -> "Device"
        else -> "Room"
    }

    // --- temperature: Setpoint and Measurement share the table, told apart by DTYPE ---

    fun temperatureColumns(alias: String, prefix: String = alias) =
        "$alias.id AS ${prefix}_id, $alias.DTYPE AS ${prefix}_dtype, $alias.value AS ${prefix}_value, $alias.date AS ${prefix}_date"

    fun temperature(rs: ResultSet, prefix: String): Temperature? {
        val id = rs.longOrNull("${prefix}_id") ?: return null
        val value = rs.floatOrNull("${prefix}_value")
        val date = rs.dateTime("${prefix}_date")
        val t = when (rs.getString("${prefix}_dtype")) {
            "Setpoint" -> Setpoint(value ?: 0f, date ?: LocalDateTime.MIN)
            "Measurement" -> Measurement(value ?: 0f, date ?: LocalDateTime.MIN)
            else -> Temperature()
        }
        // As stored, nulls included
        t.value = value
        t.date = date
        t.id = id
        return t
    }

    fun temperatureType(t: Temperature): String = temperatureType(t.javaClass)

    fun temperatureType(type: Class<out Temperature>): String = when (type) {
        Setpoint::class.java -> "Setpoint"
        Measurement::class.java -> "Measurement"
        else -> "Temperature"
    }

    // --- user: the type is stored as its ordinal (JPA's default for an enum) ---

    fun userColumns(alias: String, prefix: String = alias) =
        "$alias.id AS ${prefix}_id, $alias.username AS ${prefix}_username, $alias.password AS ${prefix}_password, $alias.type AS ${prefix}_type"

    fun user(rs: ResultSet, prefix: String): User? {
        val id = rs.longOrNull("${prefix}_id") ?: return null
        return User().apply {
            this.id = id
            username = rs.getString("${prefix}_username")
            password = rs.getString("${prefix}_password")
            type = rs.intOrNull("${prefix}_type")?.let { UserType.entries.getOrNull(it) }
        }
    }

    fun userType(type: UserType?): Int? = type?.ordinal

    // --- user_group ---

    fun groupColumns(alias: String, prefix: String = alias) =
        "$alias.id AS ${prefix}_id, $alias.name AS ${prefix}_name, $alias.description AS ${prefix}_description"

    fun group(rs: ResultSet, prefix: String): UserGroup? {
        val id = rs.longOrNull("${prefix}_id") ?: return null
        return UserGroup(rs.getString("${prefix}_name"), rs.getString("${prefix}_description")).apply { this.id = id }
    }
}
