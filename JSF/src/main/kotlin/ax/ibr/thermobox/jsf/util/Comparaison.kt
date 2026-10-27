package ax.ibr.thermobox.jsf.util

import ax.ibr.thermobox.common.entities.Temperature
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Several temperature curves on one time axis (Mesures page): the period is cut into equal
 * buckets and each curve gets the average of its points in each bucket, null where it has none.
 * Thousands of measures per salle become at most a couple of hundred points per curve.
 */
object Comparaison {

    data class Courbe(
        val valeurs: List<Float?>,
        val min: Float?,
        val max: Float?,
        val moyenne: Float?,
    ) : Serializable {
        /** Share of the buckets (with a value) within half a degree of [consigne], null without data. */
        fun dansLaCible(consigne: Float?): Float? {
            if (consigne == null) return null
            val connues = valeurs.filterNotNull()
            if (connues.isEmpty()) return null
            return connues.count { abs(it - consigne) < SEUIL_CIBLE }.toFloat() / connues.size
        }
    }

    /** Within half a degree of the consigne counts as reached (same as EtatSalle). */
    const val SEUIL_CIBLE = 0.5f

    /** Bucket length for a period: about 100 to 200 points whatever its length. */
    fun pas(debut: LocalDateTime, fin: LocalDateTime): Duration {
        val heures = Duration.between(debut, fin).toHours()
        return when {
            heures <= 6 -> Duration.ofMinutes(5)
            heures <= 48 -> Duration.ofMinutes(15)
            heures <= 24 * 8 -> Duration.ofHours(1)
            heures <= 24 * 31 -> Duration.ofHours(4)
            else -> Duration.ofHours(12)
        }
    }

    /** Start of each bucket from [debut] to [fin]. */
    fun instants(debut: LocalDateTime, fin: LocalDateTime, pas: Duration): List<LocalDateTime> =
        generateSequence(debut) { it.plus(pas) }.takeWhile { it.isBefore(fin) }.toList()

    fun courbe(points: List<Temperature>, debut: LocalDateTime, fin: LocalDateTime, pas: Duration): Courbe {
        val n = instants(debut, fin, pas).size
        val sommes = DoubleArray(n)
        val nombres = IntArray(n)
        val pasMs = pas.toMillis()
        val dansPeriode = points.filter { p -> p.date != null && p.value != null && !p.date!!.isBefore(debut) && p.date!!.isBefore(fin) }
        for (p in dansPeriode) {
            val i = (Duration.between(debut, p.date).toMillis() / pasMs).toInt()
            if (i in 0 until n) {
                sommes[i] += p.value!!.toDouble()
                nombres[i]++
            }
        }
        val valeurs = (0 until n).map { i -> if (nombres[i] == 0) null else (sommes[i] / nombres[i]).toFloat() }
        val brutes = dansPeriode.map { it.value!! }
        return Courbe(
            valeurs = valeurs,
            min = brutes.minOrNull(),
            max = brutes.maxOrNull(),
            moyenne = brutes.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        )
    }
}
