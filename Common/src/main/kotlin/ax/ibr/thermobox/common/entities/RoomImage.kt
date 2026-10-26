package ax.ibr.thermobox.common.entities

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Photo of a room (or of a building, a floor…), at most one per element.
 * Kept in its own table so that loading rooms never loads images.
 */
@Entity
@Table(name = "room_image")
class RoomImage(
    @OneToOne
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    var room: Room,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Lob
    @Column(name = "data", nullable = false)
    var data: ByteArray,

    /** Also used as the version in the image URL, so browsers drop their cached copy when it changes. */
    @Column(name = "updated", nullable = false)
    var updated: LocalDateTime = LocalDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
