package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Salle

/** Business operations on the Batiment → (Etage) → Salle → Boitier tree. */
interface HierarchieService {

    /** Elements without parent (buildings, plus anything not attached yet), sorted by kind then name. */
    fun getRacines(): List<Salle>

    fun getChildren(parent: Salle): List<Salle>

    fun getParent(child: Salle): Salle?

    /** Ancestors of [salle], from the building down to its direct parent. */
    fun getChemin(salle: Salle): List<Salle>

    /** Every box under [salle], at any depth ([salle] itself if it is a box). */
    fun getBoitiers(salle: Salle): List<Boitier>

    /**
     * Attaches [child] to [parent], or detaches it when [parent] is null.
     * Replaces the current parent, if any.
     *
     * @throws IllegalArgumentException if the link breaks the hierarchy rules.
     */
    fun setParent(child: Salle, parent: Salle?)
}
