package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserGroup
import ax.ibr.thermobox.persistence.dataservices.UserGroupDataService
import java.sql.Connection
import java.sql.ResultSet

/** Tables `user_group`, `user_group_member` (its users) and `user_group_access` (its elements). */
class UserGroupDataServiceJdbcImpl(db: JdbcDatabase) : UserGroupDataService, CrudJdbcService<UserGroup>(db) {

    override val table = "user_group"
    override val select = "SELECT ${Rows.groupColumns("e")} FROM user_group e"

    override fun map(rs: ResultSet) = Rows.group(rs, "e")
    override fun idOf(t: UserGroup) = t.id
    override fun setId(t: UserGroup, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: UserGroup): Long =
        cx.insert("INSERT INTO user_group (name, description) VALUES (?, ?)", t.name, t.description)

    override fun updateRow(cx: Connection, t: UserGroup) {
        cx.execute("UPDATE user_group SET name = ?, description = ? WHERE id = ?", t.name, t.description, t.id)
    }

    override fun getByName(name: String): UserGroup? = list("WHERE e.name = ? LIMIT 1", name).firstOrNull()

    override fun getMembers(group: UserGroup): List<User> {
        val id = group.id ?: return emptyList()
        return db.read { cx ->
            cx.query("SELECT ${Rows.userColumns("u")} FROM user_group_member m JOIN `user` u ON u.id = m.user_id WHERE m.user_group_id = ? ORDER BY m.id", id) {
                Rows.user(it, "u")
            }
        }
    }

    override fun getElements(group: UserGroup): List<Room> {
        val id = group.id ?: return emptyList()
        return db.read { cx ->
            cx.query("SELECT ${Rows.roomColumns("r")} FROM user_group_access a JOIN room r ON r.id = a.room_id WHERE a.user_group_id = ? ORDER BY a.id", id) {
                Rows.room(it, "r")
            }
        }
    }

    override fun getGroupsOf(user: User): List<UserGroup> {
        val id = user.id ?: return emptyList()
        return db.read { cx ->
            cx.query("SELECT ${Rows.groupColumns("g")} FROM user_group_member m JOIN user_group g ON g.id = m.user_group_id WHERE m.user_id = ? ORDER BY g.id", id) {
                Rows.group(it, "g")
            }
        }
    }

    override fun getElementsOfGroupsOf(user: User): List<Room> {
        val id = user.id ?: return emptyList()
        return db.read { cx ->
            cx.query(
                "SELECT DISTINCT ${Rows.roomColumns("r")} FROM user_group_member m " +
                    "JOIN user_group_access a ON a.user_group_id = m.user_group_id JOIN room r ON r.id = a.room_id " +
                    "WHERE m.user_id = ? ORDER BY r.id",
                id,
            ) { Rows.room(it, "r") }
        }
    }

    override fun replace(group: UserGroup, members: Collection<Long>, elements: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        db.transaction { cx ->
            cx.execute("DELETE FROM user_group_member WHERE user_group_id = ?", id)
            cx.execute("DELETE FROM user_group_access WHERE user_group_id = ?", id)
            members.distinct().forEach { cx.execute("INSERT INTO user_group_member (user_group_id, user_id) VALUES (?, ?)", id, it) }
            elements.distinct().forEach { cx.execute("INSERT INTO user_group_access (user_group_id, room_id) VALUES (?, ?)", id, it) }
        }
    }

    override fun addMembers(group: UserGroup, members: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        db.transaction { cx ->
            val already = ids(cx, "SELECT user_id AS x FROM user_group_member WHERE user_group_id = ?", id)
            members.distinct().filter { it !in already }
                .forEach { cx.execute("INSERT INTO user_group_member (user_group_id, user_id) VALUES (?, ?)", id, it) }
        }
    }

    override fun removeMembers(group: UserGroup, members: Collection<Long>) {
        val id = group.id ?: return
        val users = members.distinct()
        if (users.isEmpty()) return
        db.transaction { cx ->
            cx.execute("DELETE FROM user_group_member WHERE user_group_id = ? AND user_id IN (${placeholders(users.size)})", id, *users.toTypedArray())
        }
    }

    override fun addElements(group: UserGroup, elements: Collection<Long>) {
        val id = requireNotNull(group.id) { "Group not saved" }
        db.transaction { cx ->
            val already = ids(cx, "SELECT room_id AS x FROM user_group_access WHERE user_group_id = ?", id)
            elements.distinct().filter { it !in already }
                .forEach { cx.execute("INSERT INTO user_group_access (user_group_id, room_id) VALUES (?, ?)", id, it) }
        }
    }

    override fun removeElements(group: UserGroup, elements: Collection<Long>) {
        val id = group.id ?: return
        val rooms = elements.distinct()
        if (rooms.isEmpty()) return
        db.transaction { cx ->
            cx.execute("DELETE FROM user_group_access WHERE user_group_id = ? AND room_id IN (${placeholders(rooms.size)})", id, *rooms.toTypedArray())
        }
    }

    override fun deleteGroup(group: UserGroup) {
        val id = group.id ?: return
        db.transaction { cx ->
            cx.execute("DELETE FROM user_group_member WHERE user_group_id = ?", id)
            cx.execute("DELETE FROM user_group_access WHERE user_group_id = ?", id)
            cx.execute("DELETE FROM user_group WHERE id = ?", id)
        }
    }

    override fun forgetUser(user: User) {
        val id = user.id ?: return
        db.transaction { cx -> cx.execute("DELETE FROM user_group_member WHERE user_id = ?", id) }
    }

    override fun forgetRoom(room: Room) {
        val id = room.id ?: return
        db.transaction { cx -> cx.execute("DELETE FROM user_group_access WHERE room_id = ?", id) }
    }

    private fun ids(cx: Connection, sql: String, id: Long): Set<Long> = cx.query(sql, id) { it.getLong("x") }.toSet()
}
