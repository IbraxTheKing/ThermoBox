package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.UserDataService
import java.sql.Connection
import java.sql.ResultSet

/** Table `user`. */
class UserDataServiceJdbcImpl(db: JdbcDatabase) : UserDataService, CrudJdbcService<User>(db) {

    override val table = "`user`"
    override val select = "SELECT ${Rows.userColumns("e")} FROM `user` e"

    override fun map(rs: ResultSet) = Rows.user(rs, "e")
    override fun idOf(t: User) = t.id
    override fun setId(t: User, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: User): Long =
        cx.insert("INSERT INTO `user` (username, password, type) VALUES (?, ?, ?)", t.username, t.password, Rows.userType(t.type))

    override fun updateRow(cx: Connection, t: User) {
        cx.execute("UPDATE `user` SET username = ?, password = ?, type = ? WHERE id = ?", t.username, t.password, Rows.userType(t.type), t.id)
    }

    override fun getByUsername(username: String): User? = list("WHERE e.username = ? ORDER BY e.id LIMIT 1", username).firstOrNull()

    override fun getByType(userType: UserType): List<User> = list("WHERE e.type = ? ORDER BY e.id", userType.ordinal)
}
