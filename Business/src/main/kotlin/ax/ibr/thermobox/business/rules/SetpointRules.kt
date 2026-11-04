package ax.ibr.thermobox.business.rules

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.business.settings.TemperatureLimits
import ax.ibr.thermobox.business.settings.TemperatureLimitsStore

/** Limits a temperature must respect, as chosen by an ADMIN ([TemperatureLimits]). */
object SetpointRules {

    /** The limits in force; replaced by the tests. */
    @Volatile var limits: () -> TemperatureLimits = { TemperatureLimitsStore.instance.get() }

    /** Before a setpoint is saved or sent to a device. */
    fun check(value: Float) {
        // NaN passes both comparisons below: « ?value=NaN » or {"value": "NaN"} was saved
        if (!value.isFinite()) throw ImpossibleValueException("Setpoint is not a number: $value")
        val r = limits().setpoint
        if (value < r.min) throw ImpossibleValueException("Setpoint too low: $value °C (min: ${r.min} °C)")
        if (value > r.max) throw TooHotException("Setpoint too high: $value °C (max: ${r.max} °C)")
    }

    /** Before a measurement is saved. */
    fun checkMeasurement(value: Float) {
        if (!value.isFinite()) throw ImpossibleValueException("Measurement is not a number: $value")
        val r = limits().measurement
        if (value < r.min) throw ImpossibleValueException("Measurement too low: $value °C (min: ${r.min} °C)")
        if (value > r.max) throw TooHotException("Measurement too high: $value °C (max: ${r.max} °C)")
    }

    /** A reading a device may report: outside, it is a sensor error and is dropped. */
    fun acceptsMeasurement(value: Float): Boolean = value in limits().measurement
}
