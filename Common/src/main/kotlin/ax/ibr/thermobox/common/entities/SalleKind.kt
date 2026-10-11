package ax.ibr.thermobox.common.entities

/** Level of a [Salle] in the building hierarchy, from the top: Batiment → Etage → Salle → Boitier. */
enum class SalleKind(val label: String, val withArticle: String) {
    BATIMENT("Bâtiment", "un bâtiment"),
    ETAGE("Étage", "un étage"),
    SALLE("Salle", "une salle"),
    BOITIER("Boîtier", "un boîtier");

    /** Kind the parent must have (the level just above), or null for a building. */
    val parentKind: SalleKind? get() = entries.getOrNull(ordinal - 1)

    /** Kind of the children (the level just below), or null for a box. */
    val childKind: SalleKind? get() = entries.getOrNull(ordinal + 1)

    fun create(name: String?): Salle = when (this) {
        BATIMENT -> Batiment(name)
        ETAGE -> Etage(name)
        SALLE -> Salle(name)
        BOITIER -> Boitier(name)
    }
}

val Salle.kind: SalleKind
    get() = when (this) {
        is Batiment -> SalleKind.BATIMENT
        is Etage -> SalleKind.ETAGE
        is Boitier -> SalleKind.BOITIER
        else -> SalleKind.SALLE
    }
