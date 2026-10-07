package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

@Entity
class SalleTempAttr(
    @ManyToOne
    @JoinColumn(name = "salle_id")
    var salle: Salle,

    @OneToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "temperature_id")
    var temperature: Temperature
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "Attr(id=$id, temperature=$temperature / salle=$salle)"
    }
}