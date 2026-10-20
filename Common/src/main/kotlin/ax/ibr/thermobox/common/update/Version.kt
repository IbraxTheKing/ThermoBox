package ax.ibr.thermobox.common.update

/**
 * Numéro de version de ThermoBox : `AA.MM.PATCH`, éventuellement suivi d'une prépublication
 * (`-nightly.N`, `-beta.N`, `-rc.N`). La forme `-nightly-N` des tags de la nightly est acceptée,
 * ainsi qu'un préfixe `v`.
 *
 * Deux versions sont égales quand leurs numéros le sont (`26.01.0` et `26.1.0`) ; [tag] garde
 * l'écriture d'origine, celle du tag GitHub.
 */
class Version private constructor(
    val tag: String,
    val annee: Int,
    val mois: Int,
    val patch: Int,
    /** nightly, beta, rc… ; null pour une version stable. */
    val prerelease: String?,
    val numeroPrerelease: Int,
) : Comparable<Version> {

    val estPrerelease: Boolean get() = prerelease != null

    /** Série mensuelle, « 26.10 » : les versions d'une même série sont compatibles entre elles. */
    val serie: String get() = "$annee.${mois.toString().padStart(2, '0')}"

    /** Mois écoulés depuis l'an 0, pour compter les séries sautées. */
    val rangSerie: Int get() = annee * 12 + mois

    fun memeSerie(autre: Version): Boolean = annee == autre.annee && mois == autre.mois

    /**
     * Type de la mise à jour qui mène de [installee] à cette version : corrective dans la même
     * série, fonctionnelle sinon. Une installation de version inconnue (build de développement)
     * ne peut rien garantir : la mise à jour est alors traitée comme fonctionnelle.
     */
    fun typeDepuis(installee: Version?): TypeMiseAJour =
        if (installee != null && memeSerie(installee)) TypeMiseAJour.CORRECTIVE else TypeMiseAJour.FONCTIONNELLE

    override fun compareTo(other: Version): Int {
        compareValues(annee, other.annee).let { if (it != 0) return it }
        compareValues(mois, other.mois).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A stable version comes after all of its pre-releases.
        if (prerelease == null || other.prerelease == null) return compareValues(prerelease == null, other.prerelease == null)
        compareValues(rang(prerelease), rang(other.prerelease)).let { if (it != 0) return it }
        compareValues(prerelease, other.prerelease).let { if (it != 0) return it }
        return compareValues(numeroPrerelease, other.numeroPrerelease)
    }

    override fun equals(other: Any?): Boolean = other is Version && compareTo(other) == 0

    override fun hashCode(): Int = listOf(annee, mois, patch, prerelease, numeroPrerelease).hashCode()

    override fun toString(): String = tag

    companion object {
        // Digits, dots, letters and dashes only: a parsed tag is also safe as a directory name.
        private val FORMAT = Regex("""v?(\d{1,3})\.(\d{1,2})\.(\d{1,4})(?:-([a-z]{1,20})[.-]?(\d{1,6})?)?""", RegexOption.IGNORE_CASE)

        private fun rang(prerelease: String): Int = when (prerelease) {
            "nightly" -> 0
            "alpha" -> 1
            "beta" -> 2
            "rc" -> 3
            else -> 1
        }

        /** null si [texte] n'est pas un numéro de version ThermoBox. */
        fun parse(texte: String?): Version? {
            val t = texte?.trim() ?: return null
            val m = FORMAT.matchEntire(t) ?: return null
            val (a, mo, p, pre, n) = m.destructured
            if (mo.toInt() !in 1..12) return null
            return Version(t.removePrefix("v").removePrefix("V"), a.toInt(), mo.toInt(), p.toInt(),
                pre.lowercase().ifEmpty { null }, n.toIntOrNull() ?: 0)
        }
    }
}
