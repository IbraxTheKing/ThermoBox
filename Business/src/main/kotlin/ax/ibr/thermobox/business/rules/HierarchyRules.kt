package ax.ibr.thermobox.business.rules

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.kind

/** Which element may contain which (see [ax.ibr.thermobox.common.entities.RoomKind.parentKinds]). */
object HierarchyRules {

    /** @throws IllegalArgumentException with a message meant for the user. */
    fun checkLink(parent: Room, child: Room) {
        require(parent.id != null && child.id != null) { "Both elements must be saved before being linked." }
        require(parent.id != child.id) { "An element cannot contain itself." }

        val allowed = child.kind.parentKinds
        require(allowed.isNotEmpty()) { "A building cannot be attached to another element." }
        require(parent.kind in allowed) {
            "${child.kind.withArticle.replaceFirstChar { it.uppercase() }} attaches to " +
                "${allowed.joinToString(" or ") { it.withArticle }}, not to ${parent.kind.withArticle}."
        }
    }
}
