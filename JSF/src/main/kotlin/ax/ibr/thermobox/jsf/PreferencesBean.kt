package ax.ibr.thermobox.jsf

import jakarta.enterprise.context.RequestScoped
import jakarta.faces.context.FacesContext
import jakarta.inject.Inject
import jakarta.inject.Named
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Unite
import jakarta.servlet.http.HttpServletRequest

/**
 * Display preferences of this browser, from cookies written by the page script (tbPrefs in
 * thermobox.js): read on the server so the page arrives with them already applied.
 */
@Named
@RequestScoped
class PreferencesBean {

    @Inject
    private lateinit var login: LoginBean

    private val request: HttpServletRequest
        get() = FacesContext.getCurrentInstance().externalContext.request as HttpServletRequest

    private fun cookie(nom: String): String? = request.cookies?.firstOrNull { it.name == nom }?.value

    /** false when the user turned the animations off (layout.xhtml: data-motion="off"). */
    val animations: Boolean by lazy { cookie(ANIMATIONS_COOKIE) != "off" }

    /** Temperatures in °F (cookie thermobox-unite, see Unite). */
    val fahrenheit: Boolean by lazy { cookie(Unite.COOKIE) == "F" }

    /**
     * The colours follow the state of what is shown (a stable salle turns the page green…).
     * Off: the palette stays the same everywhere (layout.xhtml: data-teinte="off").
     */
    val teinte: Boolean by lazy { cookie(TEINTE_COOKIE) != "off" }

    /** Keyboard shortcuts (tbClavier), on unless turned off (cookie thermobox-clavier). */
    val clavier: Boolean by lazy { cookie("thermobox-clavier") != "off" }

    /** Look of the pages for everyone, chosen by an ADMIN: "verre" or "panneau" (layout.xhtml: data-style). */
    val style: String get() = ReglagesSite.style.code

    /** Switch of the Préférences page (ADMIN): control-panel look for everyone. */
    var panneau: Boolean = ReglagesSite.style == ReglagesSite.Style.PANNEAU

    /** p:ajax of the switch: saves [panneau] for every user. */
    fun enregistrerStyle() {
        if (!login.require(login.isAdmin)) return
        try {
            ReglagesSite.changerStyle(if (panneau) ReglagesSite.Style.PANNEAU else ReglagesSite.Style.VERRE)
            // tbPrefs.style applies it to this page at once
            org.primefaces.PrimeFaces.current().ajax().addCallbackParam("style", ReglagesSite.style.code)
            Faces.info(if (panneau) "Style panneau de configuration pour tout le monde" else "Style d'origine pour tout le monde")
        } catch (e: Exception) {
            panneau = !panneau
            Faces.error("Réglage non enregistré", "Le dossier de données n'est pas accessible en écriture : ${e.message}")
        }
    }

    companion object {
        const val ANIMATIONS_COOKIE = "thermobox-animations"
        const val TEINTE_COOKIE = "thermobox-teinte"
    }
}
