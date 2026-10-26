package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.thermobox.persistence.dataservices.RoomImageDataService
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime

/** Table `room_image`, read with its room. */
class RoomImageDataServiceJdbcImpl(db: JdbcDatabase) : RoomImageDataService, CrudJdbcService<RoomImage>(db) {

    override val table = "room_image"
    override val select = "SELECT e.id AS e_id, e.content_type AS e_content_type, e.data AS e_data, e.updated AS e_updated, " +
        "${Rows.roomColumns("r")} FROM room_image e JOIN room r ON r.id = e.room_id"

    override fun map(rs: ResultSet): RoomImage? {
        val room = Rows.room(rs, "r") ?: return null
        return RoomImage(room, rs.getString("e_content_type"), rs.getBytes("e_data"), rs.dateTime("e_updated") ?: LocalDateTime.now())
            .apply { id = rs.getLong("e_id") }
    }

    override fun idOf(t: RoomImage) = t.id
    override fun setId(t: RoomImage, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: RoomImage): Long = cx.insert(
        "INSERT INTO room_image (room_id, content_type, data, updated) VALUES (?, ?, ?, ?)",
        saved(t.room.id), t.contentType, t.data, t.updated,
    )

    override fun updateRow(cx: Connection, t: RoomImage) {
        cx.execute(
            "UPDATE room_image SET room_id = ?, content_type = ?, data = ?, updated = ? WHERE id = ?",
            saved(t.room.id), t.contentType, t.data, t.updated, t.id,
        )
    }

    override fun getByRoom(room: Room): RoomImage? {
        val id = room.id ?: return null
        return list("WHERE e.room_id = ? LIMIT 1", id).firstOrNull()
    }

    /** Without reading the images themselves. */
    override fun getDates(): Map<Long, LocalDateTime> = db.read { cx ->
        cx.query("SELECT room_id, updated FROM room_image") { rs -> rs.dateTime("updated")?.let { rs.getLong("room_id") to it } }
    }.toMap()
}
