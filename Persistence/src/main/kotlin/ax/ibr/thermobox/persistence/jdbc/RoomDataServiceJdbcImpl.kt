package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.persistence.dataservices.RoomDataService
import java.sql.Connection
import java.sql.ResultSet

/** Table `room`: buildings, floors, rooms and devices (DTYPE). */
class RoomDataServiceJdbcImpl(db: JdbcDatabase) : RoomDataService, CrudJdbcService<Room>(db) {

    override val table = "room"
    override val select = "SELECT ${Rows.roomColumns("e")} FROM room e"

    override fun map(rs: ResultSet) = Rows.room(rs, "e")
    override fun idOf(t: Room) = t.id
    override fun setId(t: Room, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: Room): Long =
        cx.insert("INSERT INTO room (DTYPE, name) VALUES (?, ?)", Rows.roomType(t), t.name)

    override fun updateRow(cx: Connection, t: Room) {
        cx.execute("UPDATE room SET DTYPE = ?, name = ? WHERE id = ?", Rows.roomType(t), t.name, t.id)
    }

    /** Elements of exactly [c]'s kind (a Room gives the rooms, not the buildings). */
    override fun getAllByType(c: Room): List<Room> = list("WHERE e.DTYPE = ? ORDER BY e.id", Rows.roomType(c))

    override fun getByName(name: String): Room? = list("WHERE e.name = ? ORDER BY e.id LIMIT 1", name).firstOrNull()
}
