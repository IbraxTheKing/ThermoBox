package ax.ibr.thermobox.jsf.util

import ax.ibr.thermobox.common.entities.Temperature
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/**
 * Small temperature curve of a dashboard card, drawn as inline SVG paths in a
 * [WIDTH] × [HEIGHT] box (stretched to the card width by the page). Positions of the HTML
 * overlays (last point, consigne line) are given in percent of that box.
 */
data class Sparkline(
    val ligne: String,
    val aire: String,
    val dernierX: Double,
    val dernierY: Double,
    /** Null when there is no consigne, or when it is so far from the curve that it would flatten it. */
    val consigneY: Double?,
    /** Change over the last hour, null if the curve doesn't go back that far. */
    val variation: Float?,
) : Serializable {

    val variationLabel: String get() = variation?.let { String.format(Locale.FRANCE, "%+.1f° en 1 h", it) }.orEmpty()

    /** pi-arrow-up-right, pi-arrow-down-right or pi-arrow-right */
    val variationIcon: String
        get() = when {
            variation == null -> ""
            variation >= 0.2f -> "pi-arrow-up-right"
            variation <= -0.2f -> "pi-arrow-down-right"
            else -> "pi-arrow-right"
        }

    companion object {
        const val WIDTH = 100.0
        const val HEIGHT = 40.0
        private const val PADDING = 4.0

        /** The period is cut in this many slots, each drawn as the average of its measures. */
        private const val SLOTS = 48

        /** Smallest vertical range: below it, a few tenths of a degree would look like big swings. */
        private const val MIN_RANGE = 1.5f

        /** The consigne stretches the scale only if it is this close to the curve. */
        private const val CONSIGNE_REACH = 3f

        /** @return null when there are fewer than two slots with measures. */
        fun of(mesures: List<Temperature>, debut: LocalDateTime, fin: LocalDateTime, consigne: Float?): Sparkline? {
            val duree = Duration.between(debut, fin).toMillis().toDouble()
            if (duree <= 0) return null
            fun position(date: LocalDateTime) = Duration.between(debut, date).toMillis() / duree

            val points = mesures
                .filter { it.date != null && it.value != null && !it.date!!.isBefore(debut) && !it.date!!.isAfter(fin) }
                .groupBy { minOf(SLOTS - 1, (position(it.date!!) * SLOTS).toInt()) }
                .toSortedMap()
                .map { (slot, slotMesures) -> (slot + 0.5) / SLOTS to slotMesures.map { it.value!! }.average().toFloat() }
            if (points.size < 2) return null

            var bas = points.minOf { it.second }
            var haut = points.maxOf { it.second }
            val consigneVisible = consigne?.takeIf { it in (bas - CONSIGNE_REACH)..(haut + CONSIGNE_REACH) }
            if (consigneVisible != null) {
                bas = minOf(bas, consigneVisible)
                haut = maxOf(haut, consigneVisible)
            }
            if (haut - bas < MIN_RANGE) {
                val milieu = (haut + bas) / 2
                bas = milieu - MIN_RANGE / 2
                haut = milieu + MIN_RANGE / 2
            }

            fun x(p: Double) = p * WIDTH
            fun y(v: Float) = PADDING + (haut - v) / (haut - bas) * (HEIGHT - 2 * PADDING)

            val ligne = points.mapIndexed { i, (p, v) -> "${if (i == 0) "M" else "L"}${num(x(p))} ${num(y(v))}" }
                .joinToString(" ")
            val aire = "$ligne L${num(x(points.last().first))} $HEIGHT L${num(x(points.first().first))} $HEIGHT Z"

            val (dernierePosition, derniereValeur) = points.last()
            val ilYAUneHeure = dernierePosition - Duration.ofHours(1).toMillis() / duree
            val variation = points.lastOrNull { it.first <= ilYAUneHeure + 0.5 / SLOTS }
                ?.takeIf { it !== points.last() }
                ?.let { derniereValeur - it.second }

            return Sparkline(
                ligne = ligne,
                aire = aire,
                dernierX = x(dernierePosition) / WIDTH * 100,
                dernierY = y(derniereValeur) / HEIGHT * 100,
                consigneY = consigneVisible?.let { y(it) / HEIGHT * 100 },
                variation = variation,
            )
        }

        // SVG needs a dot as decimal separator, whatever the server locale.
        private fun num(v: Double) = String.format(Locale.ROOT, "%.2f", v)
    }
}
