package ax.ibr.thermobox.jsf.util

import jakarta.faces.context.FacesContext
import jakarta.servlet.http.HttpServletRequest
import java.util.Locale

/**
 * Unit the temperatures are shown in, chosen on the Préférences page (cookie thermobox-unite).
 * Everything is stored in Celsius: only the display and the entered values are converted.
 */
enum class Unite(val symbole: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    /** A temperature, from Celsius. */
    fun depuisCelsius(c: Float): Float = if (this == FAHRENHEIT) c * 9f / 5f + 32f else c

    /** A temperature entered in this unit, back to Celsius. */
    fun versCelsius(v: Float): Float = if (this == FAHRENHEIT) (v - 32f) * 5f / 9f else v

    /** A difference of temperatures (no offset: +1 °C is +1.8 °F). */
    fun ecartDepuisCelsius(d: Float): Float = if (this == FAHRENHEIT) d * 9f / 5f else d

    /** « 21,5 », or « — » without a value. */
    fun texte(c: Number?): String = c?.let { String.format(Locale.FRANCE, "%.1f", depuisCelsius(it.toFloat())) } ?: "—"

    /** « +0,8° », empty without a value. */
    fun ecart(d: Number?): String = d?.let { String.format(Locale.FRANCE, "%+.1f°", ecartDepuisCelsius(it.toFloat())) }.orEmpty()

    companion object {
        const val COOKIE = "thermobox-unite"

        /** Unit of the current request; Celsius outside a page (tests, background work). */
        fun courante(): Unite {
            // Outside a Faces request (tests, threads), even reaching FacesContext may fail
            val request = runCatching { FacesContext.getCurrentInstance()?.externalContext?.request }.getOrNull()
                as? HttpServletRequest ?: return CELSIUS
            return if (request.cookies?.firstOrNull { it.name == COOKIE }?.value == "F") FAHRENHEIT else CELSIUS
        }
    }
}
