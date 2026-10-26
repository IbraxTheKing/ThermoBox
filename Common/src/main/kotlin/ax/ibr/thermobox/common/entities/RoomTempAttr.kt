package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

@Entity
@Table(name = "room_temperature_attr")
class RoomTempAttr(
    @ManyToOne
    @JoinColumn(name = "room_id")
    var room: Room,

    @OneToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "temperature_id")
    var temperature: Temperature
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "Attr(id=$id, temperature=$temperature / room=$room)"
    }
}