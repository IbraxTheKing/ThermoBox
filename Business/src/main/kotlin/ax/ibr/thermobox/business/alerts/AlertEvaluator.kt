package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertDevice
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.entities.Temperature
import java.time.Duration
import java.time.LocalDateTime

/** A box and its measures over the window read (oldest first). */
data class DeviceReading(val id: Long, val name: String?, val measures: List<Temperature>)

/**
 * A box against a threshold, from its latest measures: [exceeding] or not, [since] when its
 * latest measures started to be on that side of the threshold (the first of them), [fresh] when
 * it measured recently enough to be trusted ([AlertEvaluator.STALE]).
 */
data class DeviceStatus(
    val id: Long,
    val name: String?,
    val latest: Float?,
    val at: LocalDateTime?,
    val fresh: Boolean,
    val exceeding: Boolean,
    val since: LocalDateTime?,
) {
    fun toAlertDevice() = AlertDevice(id, name, latest, at, exceeding && fresh)
}

/**
 * The decisions of the alerts, without any I/O (the monitor reads the measures and keeps the
 * events): which rule applies to a room, whether it is in excess, whether it is back to normal.
 *
 * A room is in excess when one of its boxes has been above the threshold (setpoint + tolerance)
 * for the minimum duration; it is back to normal when every box that still measures has been
 * at or under it for the clearing duration. A box whose latest measure is older than [STALE]
 * (offline, out of battery) neither triggers nor resolves anything. The setpoint is the one of
 * the room now: if it is changed during an excess, the new threshold applies to the measures
 * from then on (raising it above the temperature resolves the alert after the clearing duration).
 */
object AlertEvaluator {

    /** A box whose latest measure is older than this does not count. */
    val STALE: Duration = Duration.ofMinutes(30)

    /**
     * The rules that apply to a room: among the enabled rules that cover it, the nearest ones (a
     * rule on the room before one on its floor, its building, then the global ones). Several rules
     * at the same distance all apply, each with its own alerts and channels.
     *
     * [ancestors]: the ids of the elements above the room, from its building down to its parent.
     */
    fun applicable(rules: List<AlertRule>, roomId: Long, ancestors: List<Long>): List<AlertRule> {
        val chain = listOf(roomId) + ancestors.reversed()
        val distances = rules.filter { it.enabled }.mapNotNull { r ->
            val d = if (r.global) chain.size else chain.indexOfFirst { it in r.scope }.takeIf { it >= 0 }
            d?.let { r to it }
        }
        val nearest = distances.minOfOrNull { it.second } ?: return emptyList()
        return distances.filter { it.second == nearest }.map { it.first }
    }

    fun status(d: DeviceReading, threshold: Float, now: LocalDateTime): DeviceStatus {
        val measures = d.measures.filter { it.value != null && it.date != null }
        val last = measures.lastOrNull() ?: return DeviceStatus(d.id, d.name, null, null, false, false, null)
        val exceeding = last.value!! > threshold
        var since = last.date!!
        for (m in measures.asReversed()) {
            if ((m.value!! > threshold) != exceeding) break
            since = m.date!!
        }
        val fresh = Duration.between(last.date, now) <= STALE
        return DeviceStatus(d.id, d.name, last.value, last.date, fresh, exceeding, since)
    }

    /**
     * When the room's excess started (the earliest box among those above the threshold for at
     * least [minDuration]), null when it is not in excess.
     */
    fun excessSince(devices: List<DeviceStatus>, minDuration: Duration, now: LocalDateTime): LocalDateTime? =
        devices.filter { it.fresh && it.exceeding && it.since != null && Duration.between(it.since, now) >= minDuration }
            .minOfOrNull { it.since!! }

    /** Every box that still measures has been at or under the threshold for [clearAfter] (and at least one measures). */
    fun backToNormal(devices: List<DeviceStatus>, clearAfter: Duration, now: LocalDateTime): Boolean {
        val fresh = devices.filter { it.fresh }
        return fresh.isNotEmpty() && fresh.all { !it.exceeding && it.since != null && Duration.between(it.since, now) >= clearAfter }
    }

    /** How far back the measures must be read for [rules]: enough to see their durations, and a box's freshness. */
    fun window(rules: List<AlertRule>): Duration {
        val longest = rules.maxOfOrNull { maxOf(it.minDurationSeconds, it.clearAfterSeconds) }?.toLong() ?: 0
        return Duration.ofSeconds(longest).plus(STALE).coerceAtMost(MAX_WINDOW)
    }

    /** The longest durations of a rule (6 h) and the freshness. */
    val MAX_WINDOW: Duration = Duration.ofHours(6).plus(STALE)
}
