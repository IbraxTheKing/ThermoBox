package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.update.EtatEtape
import ax.ibr.thermobox.common.update.EtatInstallation
import ax.ibr.thermobox.common.update.EtatMisesAJour
import ax.ibr.thermobox.common.update.NiveauControle
import ax.ibr.thermobox.common.update.OptionsInstallation
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.TypeMiseAJour
import ax.ibr.thermobox.jsf.auth.LoginBean
import ax.ibr.thermobox.jsf.util.Faces
import ax.ibr.thermobox.jsf.util.Markdown
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.RequestScoped
import jakarta.faces.view.ViewScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.primefaces.PrimeFaces
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Administration page of the updates (ADMIN, /admin/updates.xhtml). */
@Named
@ViewScoped
class UpdateBean : Serializable {

    @Inject
    private lateinit var login: LoginBean

    // Not Serializable, and a singleton anyway: looked up every time.
    private val service get() = BusinessFactory.getUpdateService()

    @Transient
    var etat: EtatMisesAJour? = null

    /** Version picked for installation (dialog). */
    @Transient
    var preparation: Preparation? = null

    var sauvegarder: Boolean = true
    var risquesAcceptes: Boolean = false

    @PostConstruct
    fun init() = rafraichir()

    /** Also called by the p:poll while an installation runs. */
    fun rafraichir() {
        etat = service.getEtat()
    }

    fun verifier() {
        if (!login.require(login.isAdmin)) return
        val e = service.verifier()
        etat = e
        when {
            e.erreur != null -> Faces.error("Vérification impossible", e.erreur)
            e.miseAJourDisponible -> Faces.info("Nouvelle version disponible", e.disponibles.first().version.tag)
            else -> Faces.info("ThermoBox est à jour")
        }
    }

    fun preparer(tag: String) {
        if (!login.require(login.isAdmin)) return
        try {
            preparation = service.preparer(tag)
            sauvegarder = true
            risquesAcceptes = false
            PrimeFaces.current().ajax().addCallbackParam("ok", true)
        } catch (e: IllegalArgumentException) {
            Faces.error("Version indisponible", e.message)
            rafraichir()
        }
    }

    fun installer() {
        if (!login.require(login.isAdmin)) return
        val p = preparation ?: return
        try {
            service.installer(p.version.version.tag, login.currentUser?.username.orEmpty(), OptionsInstallation(sauvegarder, risquesAcceptes))
            Faces.info("Installation de ${p.version.version} lancée", "Le déroulement s'affiche ci-dessous.")
            PrimeFaces.current().ajax().addCallbackParam("ok", true)
        } catch (e: IllegalArgumentException) {
            Faces.warn("Installation non lancée", e.message)
        } catch (e: IllegalStateException) {
            Faces.error("Installation impossible", e.message)
        }
        rafraichir()
    }

    /** The page polls while an installation runs, and reloads once the interface is redeployed. */
    val isSuivi: Boolean
        get() = etat?.installation?.etat.let { it == EtatInstallation.EN_COURS || it == EtatInstallation.REDEMARRAGE }

    fun notes(texte: String?): String = Markdown.versHtml(texte)

    fun date(instant: Instant?): String = instant?.let { DATE.format(it) } ?: "jamais"

    fun severiteType(type: TypeMiseAJour): String = if (type == TypeMiseAJour.CORRECTIVE) "success" else "warning"

    fun iconeControle(niveau: NiveauControle): String = when (niveau) {
        NiveauControle.OK -> "pi-check-circle"
        NiveauControle.INFO -> "pi-info-circle"
        NiveauControle.AVERTISSEMENT -> "pi-exclamation-triangle"
        NiveauControle.BLOQUANT -> "pi-ban"
    }

    fun iconeEtape(etat: EtatEtape): String = when (etat) {
        EtatEtape.EN_ATTENTE -> "pi-circle"
        EtatEtape.EN_COURS -> "pi-spin pi-spinner"
        EtatEtape.OK -> "pi-check"
        EtatEtape.IGNOREE -> "pi-minus"
        EtatEtape.ECHEC -> "pi-times"
    }

    fun severiteInstallation(etat: EtatInstallation): String = when (etat) {
        EtatInstallation.EN_COURS, EtatInstallation.REDEMARRAGE -> "info"
        EtatInstallation.TERMINEE -> "success"
        EtatInstallation.ECHEC -> "danger"
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRANCE).withZone(ZoneId.systemDefault())
    }
}

/** The rail badge and the banner shown to admins when a version is out. */
@Named
@RequestScoped
class UpdateNoticeBean {

    @Inject
    private lateinit var login: LoginBean

    private val etat: EtatMisesAJour? by lazy {
        if (login.isAdmin) runCatching { BusinessFactory.getUpdateService().getEtat() }.getOrNull() else null
    }

    val isDisponible: Boolean get() = etat?.miseAJourDisponible == true

    /** « 26.11.0 (fonctionnelle) et 26.10.2 (corrective) » */
    val resume: String
        get() = listOfNotNull(etat?.fonctionnelle, etat?.corrective)
            .joinToString(" et ") { "${it.version} (${it.type.label.lowercase()})" }
}
