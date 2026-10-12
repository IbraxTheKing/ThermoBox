package ax.ibr.thermobox.business.rules

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.kind

/** Which element may contain which (see [ax.ibr.thermobox.common.entities.SalleKind.parentKinds]). */
object HierarchieRules {

    /** @throws IllegalArgumentException with a message meant for the user. */
    fun checkLink(parent: Salle, child: Salle) {
        require(parent.id != null && child.id != null) { "Les deux éléments doivent être enregistrés avant d'être liés." }
        require(parent.id != child.id) { "Un élément ne peut pas se contenir lui-même." }

        val allowed = child.kind.parentKinds
        require(allowed.isNotEmpty()) { "Un bâtiment ne peut pas être rattaché à un autre élément." }
        require(parent.kind in allowed) {
            "${child.kind.withArticle.replaceFirstChar { it.uppercase() }} se rattache à " +
                "${allowed.joinToString(" ou ") { it.withArticle }}, pas à ${parent.kind.withArticle}."
        }
    }
}
