package ax.ibr.thermobox.jsf

import jakarta.enterprise.context.RequestScoped
import jakarta.faces.context.FacesContext
import jakarta.inject.Named
import jakarta.servlet.http.HttpServletRequest

/**
 * Light or dark theme, as chosen with the toggle (cookie written by the page script, see
 * layout.xhtml). Read on the server so the page arrives in the right theme, PrimeFaces
 * stylesheet included: saga in light mode, vela (its dark twin) in dark mode.
 */
@Named
@RequestScoped
class ThemeBean {

    val isDark: Boolean by lazy {
        val request = FacesContext.getCurrentInstance().externalContext.request as HttpServletRequest
        request.cookies?.firstOrNull { it.name == COOKIE }?.value == "dark"
    }

    val mode: String get() = if (isDark) "dark" else "light"

    /** Value of the primefaces.THEME context parameter (web.xml). */
    val primefacesTheme: String get() = if (isDark) "vela" else "saga"

    companion object {
        const val COOKIE = "thermobox-theme"
    }
}
