package ax.ibr.thermobox.common.entities

/** Level of a [Salle] in the building hierarchy, from the top: Batiment → (Etage) → Salle → Boitier. */
enum class SalleKind(val label: String, val withArticle: String) {
    BATIMENT("Bâtiment", "un bâtiment"),
    ETAGE("Étage", "un étage"),
    SALLE("Salle", "une salle"),
    BOITIER("Boîtier", "un boîtier");

    /** Kinds that may contain this one: a salle can sit on an étage or directly in a bâtiment. */
    val parentKinds: List<SalleKind>
        get() = when (this) {
            BATIMENT -> emptyList()
            ETAGE -> listOf(BATIMENT)
            SALLE -> listOf(ETAGE, BATIMENT)
            BOITIER -> listOf(SALLE)
        }

    /** Kinds this one may contain. */
    val childKinds: List<SalleKind> get() = entries.filter { this in it.parentKinds }

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
