package ax.ibr.thermobox.restserver.notifications

import ax.ibr.thermobox.common.entities.Temperature
import java.time.Duration
import java.time.LocalDateTime

/** Where a room stands against its setpoint (the same thresholds as the clients' « state »). */
enum class SetpointState {
    /** Within [SetpointWatch.THRESHOLD] of the setpoint: respected. */
    STABLE,
    /** At least [SetpointWatch.THRESHOLD] below it. */
    HEATING,
    /** At least [SetpointWatch.THRESHOLD] above it. */
    TOO_HOT,
}

/** A room went from [previous] to [state] at [at]; [temperature] when it was confirmed. */
data class SetpointChange(
    val previous: SetpointState,
    val state: SetpointState,
    val at: LocalDateTime,
    val temperature: Float,
)

/**
 * When a room starts or stops respecting its setpoint, from its average curve: the changes of
 * [SetpointState] along it. A state counts once it has lasted [HOLD] (a measure that crosses the
 * line and comes back is not a change), and is dated from its first measure. The state before
 * the period asked for is read from the curve before it (LOOKBACK).
 *
 * A change belongs to the period in which it became known (its start + [HOLD]), not to the one
 * in which it started: an app that asks every 2 minutes « since my last question » would
 * otherwise never get it (known 10 minutes after its start, it had started before the period
 * asked for). Each change is in exactly one of a run of periods that follow each other.
 *
 * Nothing is stored: the changes are read again from the measures on each request, so a client
 * that asks « since my last visit » gets what happened meanwhile, whoever else asked.
 */
object SetpointWatch {

    /** Within half a degree of the setpoint counts as reached (the clients' TARGET_THRESHOLD). */
    const val THRESHOLD = 0.5f

    /** How long a new state must last to count. */
    val HOLD: Duration = Duration.ofMinutes(10)

    /** How far before the period the curve is read, to know the state it starts in. */
    val LOOKBACK: Duration = Duration.ofHours(1)

    fun stateOf(temperature: Float, setpoint: Float): SetpointState {
        val delta = temperature - setpoint
        return when {
            delta <= -THRESHOLD -> SetpointState.HEATING
            delta >= THRESHOLD -> SetpointState.TOO_HOT
            else -> SetpointState.STABLE
        }
    }

    /**
     * Changes of state along [curve] (oldest first) against [setpoint], those that became known
     * (their start + [hold]) after [since] and by [now]. A state reached by the last measures
     * counts if it has lasted [hold] by [now] (the boxes report every few minutes, not
     * continuously).
     */
    fun changes(
        curve: List<Temperature>,
        setpoint: Float,
        since: LocalDateTime,
        now: LocalDateTime,
        hold: Duration = HOLD,
    ): List<SetpointChange> {
        val result = ArrayList<SetpointChange>()
        var confirmed: SetpointState? = null
        var candidate: SetpointState? = null
        var candidateStart: LocalDateTime? = null
        var candidateLast = 0f

        fun confirm() {
            val state = candidate ?: return
            val start = candidateStart ?: return
            val previous = confirmed
            val known = start.plus(hold)
            if (previous != null && previous != state && known.isAfter(since) && !known.isAfter(now)) {
                result += SetpointChange(previous, state, start, candidateLast)
            }
            confirmed = state
        }

        for (t in curve) {
            val value = t.value ?: continue
            val date = t.date ?: continue
            val state = stateOf(value, setpoint)
            if (state != candidate) {
                candidate = state
                candidateStart = date
            }
            candidateLast = value
            if (candidate != confirmed && Duration.between(candidateStart, date) >= hold) confirm()
        }
        // The last state, still going on now
        val start = candidateStart
        if (candidate != confirmed && start != null && Duration.between(start, now) >= hold) confirm()
        return result
    }
}
