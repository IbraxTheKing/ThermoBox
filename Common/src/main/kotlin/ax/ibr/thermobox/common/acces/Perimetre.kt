package ax.ibr.thermobox.common.acces

import ax.ibr.thermobox.common.entities.Salle
import java.io.Serializable

/**
 * What a user may see, worked out from their accesses and the hierarchy.
 *
 * - accessible: granted directly, or contained (at any depth) in a granted element. Its data,
 *   its page and, for a gestionnaire, its consigne.
 * - visible: accessible, or an ancestor of an accessible element. Only its name, to place the
 *   accessible elements (breadcrumb, tree, dashboard groups); never its data, since an average
 *   over a bâtiment would tell about salles the user may not see.
 */
class Perimetre(
    val tout: Boolean,
    private val accessibles: Set<Long> = emptySet(),
    private val visibles: Set<Long> = emptySet(),
) : Serializable {

    fun peutVoir(id: Long?): Boolean = id != null && (tout || id in accessibles)

    fun peutVoir(salle: Salle?): Boolean = peutVoir(salle?.id)

    fun estVisible(salle: Salle?): Boolean = salle?.id?.let { tout || it in visibles || it in accessibles } == true

    val nombreAccessibles: Int get() = accessibles.size

    companion object {
        val TOUT = Perimetre(true)
        val AUCUN = Perimetre(false)
    }
}
