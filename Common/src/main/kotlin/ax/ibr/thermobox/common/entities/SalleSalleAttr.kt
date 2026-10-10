package ax.ibr.thermobox.common.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "salle_attr")
class SalleSalleAttr(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salleA_id", nullable = false)
    var salleA: Salle,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salleB_id", nullable = false)
    var salleB: Salle
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "${salleA.id} <-> ${salleB.id}"
    }
}