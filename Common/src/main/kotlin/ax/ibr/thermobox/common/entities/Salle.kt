package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

@Entity
class Salle(var name: String?) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

}