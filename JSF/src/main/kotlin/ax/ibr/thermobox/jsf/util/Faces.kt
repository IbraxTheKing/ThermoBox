package ax.ibr.thermobox.jsf.util

import jakarta.faces.application.FacesMessage
import jakarta.faces.context.FacesContext

/** Raccourcis pour les messages JSF (affichés par le p:growl du layout). */
object Faces {
    private val ctx: FacesContext get() = FacesContext.getCurrentInstance()

    fun info(summary: String, detail: String? = null) = add(FacesMessage.SEVERITY_INFO, summary, detail)
    fun warn(summary: String, detail: String? = null) = add(FacesMessage.SEVERITY_WARN, summary, detail)
    fun error(summary: String, detail: String? = null) = add(FacesMessage.SEVERITY_ERROR, summary, detail)

    /** Garde les messages à travers un redirect (faces-redirect=true). */
    fun keepMessages() {
        ctx.externalContext.flash.isKeepMessages = true
    }

    private fun add(severity: FacesMessage.Severity, summary: String, detail: String?) {
        ctx.addMessage(null, FacesMessage(severity, summary, detail))
    }
}
