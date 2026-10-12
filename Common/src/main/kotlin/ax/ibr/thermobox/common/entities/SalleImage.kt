package ax.ibr.thermobox.common.entities

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Photo of a salle (or of a bâtiment, an étage…), at most one per element.
 * Kept in its own table so that loading salles never loads images.
 */
@Entity
@Table(name = "salle_image")
class SalleImage(
    @OneToOne
    @JoinColumn(name = "salle_id", nullable = false, unique = true)
    var salle: Salle,

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
