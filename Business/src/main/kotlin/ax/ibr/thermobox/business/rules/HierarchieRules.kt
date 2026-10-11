package ax.ibr.thermobox.business.rules

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.kind

/** Which element may contain which: each level only contains the level just below it. */
object HierarchieRules {

    /** @throws IllegalArgumentException with a message meant for the user. */
    fun checkLink(parent: Salle, child: Salle) {
        require(parent.id != null && child.id != null) { "Les deux éléments doivent être enregistrés avant d'être liés." }
        require(parent.id != child.id) { "Un élément ne peut pas se contenir lui-même." }

        val expected = child.kind.parentKind
            ?: throw IllegalArgumentException("Un bâtiment ne peut pas être rattaché à un autre élément.")
        require(parent.kind == expected) {
            "${child.kind.withArticle.replaceFirstChar { it.uppercase() }} se rattache à ${expected.withArticle}, " +
                "pas à ${parent.kind.withArticle}."
        }
    }
}
