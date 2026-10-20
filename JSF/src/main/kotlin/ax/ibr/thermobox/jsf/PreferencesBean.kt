package ax.ibr.thermobox.jsf

import jakarta.enterprise.context.RequestScoped
import jakarta.faces.context.FacesContext
import jakarta.inject.Named
import jakarta.servlet.http.HttpServletRequest

/**
 * Display preferences of this browser, from cookies written by the page script (tbPrefs in
 * thermobox.js): read on the server so the page arrives with them already applied.
 */
@Named
@RequestScoped
class PreferencesBean {

    /** false when the user turned the animations off (layout.xhtml: data-motion="off"). */
    val animations: Boolean by lazy {
        val request = FacesContext.getCurrentInstance().externalContext.request as HttpServletRequest
        request.cookies?.firstOrNull { it.name == ANIMATIONS_COOKIE }?.value != "off"
    }

    companion object {
        const val ANIMATIONS_COOKIE = "thermobox-animations"
    }
}
