package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

// Open because Floor, Building and Device extend it (single table, one row per kind).
@Entity
@Table(name = "room")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "DTYPE",
    discriminatorType = DiscriminatorType.STRING
)
open class Room() {

    @Column(name = "name")
    var name: String? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "Room(id=$id, name=$name)"
    }

    constructor(name: String?) : this() {
        this.name = name
    }

}