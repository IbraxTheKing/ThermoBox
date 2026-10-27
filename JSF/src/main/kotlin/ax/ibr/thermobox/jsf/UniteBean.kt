package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.jsf.util.Unite
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Named

/**
 * Temperatures in the pages, in the unit of the Préférences page: #{unite.t(salle.moyenne)}
 * #{unite.symbole}. Values come in Celsius.
 */
@Named("unite")
@RequestScoped
class UniteBean {

    val unite: Unite by lazy { Unite.courante() }

    val symbole: String get() = unite.symbole
    val fahrenheit: Boolean get() = unite == Unite.FAHRENHEIT

    /** « 21,5 » (or « 70,7 » in Fahrenheit), « — » without a value. */
    fun t(celsius: Number?): String = unite.texte(celsius)

    /** « +0,8° » for a difference in Celsius. */
    fun ecart(celsius: Number?): String = unite.ecart(celsius)

    /** Bounds and step of the consigne field, in this unit (5 to 35 °C). */
    val consigneMin: Double get() = if (fahrenheit) 41.0 else 5.0
    val consigneMax: Double get() = if (fahrenheit) 95.0 else 35.0
    val consigneStep: Double get() = if (fahrenheit) 1.0 else 0.5

    /** Bounds of a raw temperature (-50 to 80 °C). */
    val valeurMin: Double get() = if (fahrenheit) -58.0 else -50.0
    val valeurMax: Double get() = if (fahrenheit) 176.0 else 80.0
}
