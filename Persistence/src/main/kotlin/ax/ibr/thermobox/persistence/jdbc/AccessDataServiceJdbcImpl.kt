package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Access
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.AccessDataService
import java.sql.Connection
import java.sql.ResultSet

/** Tables `user_access` (what a limited user may see) and `restricted_access` (who is limited). */
class AccessDataServiceJdbcImpl(db: JdbcDatabase) : AccessDataService, CrudJdbcService<Access>(db) {

    override val table = "user_access"
    override val select = "SELECT e.id AS e_id, ${Rows.userColumns("u")}, ${Rows.roomColumns("r")} " +
        "FROM user_access e JOIN `user` u ON u.id = e.user_id JOIN room r ON r.id = e.room_id"

    override fun map(rs: ResultSet): Access? {
        val user = Rows.user(rs, "u") ?: return null
        val room = Rows.room(rs, "r") ?: return null
        return Access(user, room).apply { id = rs.getLong("e_id") }
    }

    override fun idOf(t: Access) = t.id
    override fun setId(t: Access, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: Access): Long =
        cx.insert("INSERT INTO user_access (user_id, room_id) VALUES (?, ?)", saved(t.user.id), saved(t.room.id))

    override fun updateRow(cx: Connection, t: Access) {
        cx.execute("UPDATE user_access SET user_id = ?, room_id = ? WHERE id = ?", saved(t.user.id), saved(t.room.id), t.id)
    }

    override fun getByUser(user: User): List<Access> {
        val id = user.id ?: return emptyList()
        return list("WHERE e.user_id = ? ORDER BY e.id", id)
    }

    /** An unsaved user is limited (it has no access yet). */
    override fun isRestricted(user: User): Boolean {
        val id = user.id ?: return true
        return db.read { cx -> cx.query("SELECT 1 FROM restricted_access WHERE user_id = ? LIMIT 1", id) { true }.isNotEmpty() }
    }

    override fun replace(user: User, restricted: Boolean, roomIds: Collection<Long>) {
        val id = requireNotNull(user.id) { "User not saved" }
        db.transaction { cx ->
            cx.execute("DELETE FROM user_access WHERE user_id = ?", id)
            cx.execute("DELETE FROM restricted_access WHERE user_id = ?", id)
            if (restricted) {
                cx.execute("INSERT INTO restricted_access (user_id) VALUES (?)", id)
                roomIds.distinct().forEach { roomId -> cx.execute("INSERT INTO user_access (user_id, room_id) VALUES (?, ?)", id, roomId) }
            }
        }
    }

    override fun deleteForRoom(room: Room) {
        val id = room.id ?: return
        db.transaction { cx -> cx.execute("DELETE FROM user_access WHERE room_id = ?", id) }
    }

    override fun deleteForUser(user: User) {
        val id = user.id ?: return
        db.transaction { cx ->
            cx.execute("DELETE FROM user_access WHERE user_id = ?", id)
            cx.execute("DELETE FROM restricted_access WHERE user_id = ?", id)
        }
    }
}
