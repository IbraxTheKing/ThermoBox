package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.persistence.dataservices.FavoriteDataService
import java.sql.Connection
import java.sql.ResultSet

/** Table `favorite`, read with its user and its room. */
class FavoriteDataServiceJdbcImpl(db: JdbcDatabase) : FavoriteDataService, CrudJdbcService<Favorite>(db) {

    override val table = "favorite"
    override val select = "SELECT e.id AS e_id, ${Rows.userColumns("u")}, ${Rows.roomColumns("r")} " +
        "FROM favorite e JOIN `user` u ON u.id = e.user_id JOIN room r ON r.id = e.room_id"

    override fun map(rs: ResultSet): Favorite? {
        val user = Rows.user(rs, "u") ?: return null
        val room = Rows.room(rs, "r") ?: return null
        return Favorite(user, room).apply { id = rs.getLong("e_id") }
    }

    override fun idOf(t: Favorite) = t.id
    override fun setId(t: Favorite, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: Favorite): Long =
        cx.insert("INSERT INTO favorite (user_id, room_id) VALUES (?, ?)", saved(t.user.id), saved(t.room.id))

    override fun updateRow(cx: Connection, t: Favorite) {
        cx.execute("UPDATE favorite SET user_id = ?, room_id = ? WHERE id = ?", saved(t.user.id), saved(t.room.id), t.id)
    }

    override fun getByUser(user: User): List<Favorite> {
        val id = user.id ?: return emptyList()
        return list("WHERE e.user_id = ? ORDER BY e.id", id)
    }

    override fun getByRoom(room: Room): List<Favorite> {
        val id = room.id ?: return emptyList()
        return list("WHERE e.room_id = ? ORDER BY e.id", id)
    }
}

/** The id of an entity another one points to: it must have been saved first. */
internal fun saved(id: Long?): Long = requireNotNull(id) { "The linked entity is not saved" }
