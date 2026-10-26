package ax.ibr.thermobox.business.rules

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException

/** Limits a setpoint must respect before being saved or sent to a device. */
object SetpointRules {

    private const val IMPOSSIBLE_MAX = 100f
    private const val IMPOSSIBLE_MIN = -10f
    private const val HOT_LIMIT = 50f

    fun check(value: Float) {
        if (value >= IMPOSSIBLE_MAX || value <= IMPOSSIBLE_MIN) {
            throw ImpossibleValueException(
                "Setpoint is impossible to reach: $value C (min: $IMPOSSIBLE_MIN C, max: $IMPOSSIBLE_MAX C)"
            )
        }
        if (value >= HOT_LIMIT) {
            throw TooHotException("Setpoint is too hot")
        }
    }
}
