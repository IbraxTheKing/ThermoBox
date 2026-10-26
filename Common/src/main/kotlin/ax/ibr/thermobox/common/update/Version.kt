package ax.ibr.thermobox.common.update

/**
 * ThermoBox version number: `YY.MM.PATCH`, possibly followed by a pre-release
 * (`-nightly.N`, `-beta.N`, `-rc.N`). The `-nightly-N` form of the nightly tags is accepted,
 * as is a `v` prefix.
 *
 * Two versions are equal when their numbers are (`26.01.0` and `26.1.0`); [tag] keeps the
 * original spelling, the GitHub tag's.
 */
class Version private constructor(
    val tag: String,
    val year: Int,
    val month: Int,
    val patch: Int,
    /** nightly, beta, rc…; null for a stable version. */
    val prerelease: String?,
    val prereleaseNumber: Int,
) : Comparable<Version> {

    val isPrerelease: Boolean get() = prerelease != null

    /** Release channel: stable, nightly, beta, rc… */
    val channel: String get() = prerelease ?: "stable"

    /** Monthly series, "26.10": the versions of one series are compatible with each other. */
    val series: String get() = "$year.${month.toString().padStart(2, '0')}"

    /** Months since year 0, to count the skipped series. */
    val seriesRank: Int get() = year * 12 + month

    fun sameSeries(other: Version): Boolean = year == other.year && month == other.month

    /**
     * Type of the update that leads from [installed] to this version: a patch within the same
     * series, a feature update otherwise. An installation of unknown version (development build)
     * can't guarantee anything: the update is then handled as a feature update.
     */
    fun typeFrom(installed: Version?): UpdateType =
        if (installed != null && sameSeries(installed)) UpdateType.PATCH else UpdateType.FEATURE

    override fun compareTo(other: Version): Int {
        compareValues(year, other.year).let { if (it != 0) return it }
        compareValues(month, other.month).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A stable version comes after all of its pre-releases.
        if (prerelease == null || other.prerelease == null) return compareValues(prerelease == null, other.prerelease == null)
        compareValues(rank(prerelease), rank(other.prerelease)).let { if (it != 0) return it }
        compareValues(prerelease, other.prerelease).let { if (it != 0) return it }
        return compareValues(prereleaseNumber, other.prereleaseNumber)
    }

    override fun equals(other: Any?): Boolean = other is Version && compareTo(other) == 0

    override fun hashCode(): Int = listOf(year, month, patch, prerelease, prereleaseNumber).hashCode()

    override fun toString(): String = tag

    companion object {
        // Digits, dots, letters and dashes only: a parsed tag is also safe as a directory name.
        private val FORMAT = Regex("""v?(\d{1,3})\.(\d{1,2})\.(\d{1,4})(?:-([a-z]{1,20})[.-]?(\d{1,6})?)?""", RegexOption.IGNORE_CASE)

        private fun rank(prerelease: String): Int = when (prerelease) {
            "nightly" -> 0
            "alpha" -> 1
            "beta" -> 2
            "rc" -> 3
            else -> 1
        }

        /** null if [text] is not a ThermoBox version number. */
        fun parse(text: String?): Version? {
            val t = text?.trim() ?: return null
            val m = FORMAT.matchEntire(t) ?: return null
            val (a, mo, p, pre, n) = m.destructured
            if (mo.toInt() !in 1..12) return null
            return Version(t.removePrefix("v").removePrefix("V"), a.toInt(), mo.toInt(), p.toInt(),
                pre.lowercase().ifEmpty { null }, n.toIntOrNull() ?: 0)
        }
    }
}
