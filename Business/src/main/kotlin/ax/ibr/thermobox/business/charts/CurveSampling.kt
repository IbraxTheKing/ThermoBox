package ax.ibr.thermobox.business.charts

import ax.ibr.thermobox.common.entities.Temperature
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Reduces a curve to a few points that draw almost the same line, so a client does not download
 * every measurement of a long period (a device measuring every minute gives 43 200 points a month).
 *
 * Largest-Triangle-Three-Buckets (Sveinn Steinarsson, 2013): the first and the last points are
 * kept; the others are cut into buckets of equal size, and each bucket keeps the point that forms
 * the largest triangle with the point kept before it and the average of the next bucket. Peaks
 * and drops survive, unlike an average per bucket, which flattens them.
 */
object CurveSampling {

    /** Fewest points a sampled curve may have (first, last, and something between). */
    const val MIN_POINTS = 3

    /**
     * At most [points] of [curve] (sorted by date), the original points themselves: never an
     * invented value. A curve that already fits is returned as it is; points without a date or a
     * value are left out.
     */
    fun sample(curve: List<Temperature>, points: Int): List<Temperature> {
        require(points >= MIN_POINTS) { "points must be at least $MIN_POINTS" }
        val data = curve.filter { it.date != null && it.value != null }
        if (data.size <= points) return data

        fun x(t: Temperature): Double = t.date!!.toEpochSecond(ZoneOffset.UTC).toDouble() + t.date!!.nano / 1e9
        fun y(t: Temperature): Double = t.value!!.toDouble()

        val out = ArrayList<Temperature>(points)
        out += data.first()
        // Buckets for the points between the first and the last one
        val bucket = (data.size - 2).toDouble() / (points - 2)
        var kept = 0
        for (i in 0 until points - 2) {
            val start = (i * bucket).toInt() + 1
            val end = minOf(((i + 1) * bucket).toInt() + 1, data.size - 1)

            // Average of the next bucket (the last point for the last bucket)
            val nextStart = end
            val nextEnd = minOf(((i + 2) * bucket).toInt() + 1, data.size)
            var ax = 0.0
            var ay = 0.0
            for (j in nextStart until nextEnd) { ax += x(data[j]); ay += y(data[j]) }
            val n = nextEnd - nextStart
            ax /= n
            ay /= n

            val px = x(data[kept])
            val py = y(data[kept])
            var best = start
            var bestArea = -1.0
            for (j in start until end) {
                val area = abs((px - ax) * (y(data[j]) - py) - (px - x(data[j])) * (ay - py))
                if (area > bestArea) { bestArea = area; best = j }
            }
            out += data[best]
            kept = best
        }
        out += data.last()
        return out
    }
}
