package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomRoomAttr
import ax.ibr.thermobox.persistence.dataservices.RoomRoomAttrDataService
import java.sql.Connection
import java.sql.ResultSet

/** Table `room_attr`: roomA (the parent) contains roomB (the child). */
class RoomRoomAttrDataServiceJdbcImpl(db: JdbcDatabase) : RoomRoomAttrDataService, CrudJdbcService<RoomRoomAttr>(db) {

    override val table = "room_attr"
    override val select = "SELECT e.id AS e_id, ${Rows.roomColumns("a")}, ${Rows.roomColumns("b")} " +
        "FROM room_attr e JOIN room a ON a.id = e.roomA_id JOIN room b ON b.id = e.roomB_id"

    override fun map(rs: ResultSet): RoomRoomAttr? {
        val a = Rows.room(rs, "a") ?: return null
        val b = Rows.room(rs, "b") ?: return null
        return RoomRoomAttr(a, b).apply { id = rs.getLong("e_id") }
    }

    override fun idOf(t: RoomRoomAttr) = t.id
    override fun setId(t: RoomRoomAttr, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: RoomRoomAttr): Long =
        cx.insert("INSERT INTO room_attr (roomA_id, roomB_id) VALUES (?, ?)", saved(t.roomA.id), saved(t.roomB.id))

    override fun updateRow(cx: Connection, t: RoomRoomAttr) {
        cx.execute("UPDATE room_attr SET roomA_id = ?, roomB_id = ? WHERE id = ?", saved(t.roomA.id), saved(t.roomB.id), t.id)
    }

    /** Links where [s] is the parent or the child. */
    override fun getByRoom(s: Room): List<RoomRoomAttr> {
        val id = s.id ?: return emptyList()
        return list("WHERE e.roomA_id = ? OR e.roomB_id = ? ORDER BY e.id", id, id)
    }

    override fun getChildren(parent: Room): List<Room> {
        val id = parent.id ?: return emptyList()
        return db.read { cx ->
            cx.query("SELECT ${Rows.roomColumns("r")} FROM room_attr l JOIN room r ON r.id = l.roomB_id WHERE l.roomA_id = ? ORDER BY l.id", id) {
                Rows.room(it, "r")
            }
        }
    }

    override fun getParent(child: Room): Room? {
        val id = child.id ?: return null
        return db.read { cx ->
            cx.query("SELECT ${Rows.roomColumns("r")} FROM room_attr l JOIN room r ON r.id = l.roomA_id WHERE l.roomB_id = ? ORDER BY l.id LIMIT 1", id) {
                Rows.room(it, "r")
            }.firstOrNull()
        }
    }
}
