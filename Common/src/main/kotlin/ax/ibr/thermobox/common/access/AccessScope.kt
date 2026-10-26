package ax.ibr.thermobox.common.access

import ax.ibr.thermobox.common.entities.Room
import java.io.Serializable

/**
 * What a user may see, worked out from their accesses and the hierarchy.
 *
 * - accessible: granted directly, or contained (at any depth) in a granted element. Its data,
 *   its page and, for a manager, its setpoint.
 * - visible: accessible, or an ancestor of an accessible element. Only its name, to place the
 *   accessible elements (breadcrumb, tree, dashboard groups); never its data, since an average
 *   over a building would tell about rooms the user may not see.
 */
class AccessScope(
    val all: Boolean,
    private val accessibleIds: Set<Long> = emptySet(),
    private val visibleIds: Set<Long> = emptySet(),
) : Serializable {

    fun canSee(id: Long?): Boolean = id != null && (all || id in accessibleIds)

    fun canSee(room: Room?): Boolean = canSee(room?.id)

    fun isVisible(room: Room?): Boolean = room?.id?.let { all || it in visibleIds || it in accessibleIds } == true

    val accessibleCount: Int get() = accessibleIds.size

    companion object {
        val ALL = AccessScope(true)
        val NONE = AccessScope(false)
    }
}
