package ax.ibr.thermobox.common.entities

import jakarta.persistence.*

// Open because Etage, Batiment and Boitier extend it (single table, one row per kind).
@Entity
@Table(name = "salle")
open class Salle() {

    @Column(name = "name")
    var name: String? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    override fun toString(): String {
        return "Salle(id=$id, name=$name)"
    }

    constructor(name: String?) : this() {
        this.name = name
    }

}