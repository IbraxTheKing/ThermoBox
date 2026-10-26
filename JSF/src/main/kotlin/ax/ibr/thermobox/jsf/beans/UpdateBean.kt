package ax.ibr.thermobox.jsf.beans

import ax.ibr.thermobox.jsf.util.Libelles

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.update.StepState
import ax.ibr.thermobox.common.update.InstallationState
import ax.ibr.thermobox.common.update.UpdateStatus
import ax.ibr.thermobox.common.update.CheckLevel
import ax.ibr.thermobox.common.update.InstallOptions
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.UpdateType
import ax.ibr.thermobox.common.update.Version
import ax.ibr.thermobox.common.update.AvailableVersion
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
    var etat: UpdateStatus? = null

    /** Version picked for installation (dialog). */
    @Transient
    var preparation: Preparation? = null

    var sauvegarder: Boolean = true
    var risquesAcceptes: Boolean = false

    /** Channel chip picked ("rc", "beta", "nightly", "stable"), null for all. */
    var canalFiltre: String? = null

    /** One chip per channel present, in this order: RC, Beta, Nightly, Stable. */
    val chipsCanaux: List<ChipCanal>
        get() {
            val toutes = etat?.let { it.available + it.olderVersions }.orEmpty()
            val parCle = toutes.groupingBy { cle(it.version) }.eachCount()
            val ordre = listOf("rc", "beta", "nightly", "stable")
            return (ordre + (parCle.keys - ordre.toSet()).sorted())
                .filter { it in parCle }
                .map { c -> ChipCanal(c, libelle(c), parCle.getValue(c), couleur(c)) }
        }

    fun filtrerCanal(c: String) {
        canalFiltre = if (canalFiltre == c) null else c
    }

    /** [liste] restricted to the picked channel. */
    fun filtrees(liste: List<AvailableVersion>?): List<AvailableVersion> =
        liste.orEmpty().filter { canalFiltre == null || cle(it.version) == canalFiltre }

    @PostConstruct
    fun init() = rafraichir()

    /** Also called by the p:poll while an installation runs. */
    fun rafraichir() {
        etat = service.getStatus()
    }

    fun verifier() {
        if (!login.require(login.isAdmin)) return
        val e = service.check()
        etat = e
        when {
            e.error != null -> Faces.error("Vérification impossible", e.error)
            e.updateAvailable -> Faces.info("Nouvelle version disponible", e.available.first().version.tag)
            else -> Faces.info("ThermoBox est à jour")
        }
    }

    fun preparer(tag: String) {
        if (!login.require(login.isAdmin)) return
        try {
            val p = service.prepare(tag)
            preparation = p
            // A corrective doesn't touch the database: the backup is offered, not ticked.
            sauvegarder = p.backupRequired
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
            service.install(p.version.version.tag, login.currentUser?.username.orEmpty(), InstallOptions(sauvegarder, risquesAcceptes))
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
        get() = etat?.installation?.state.let { it == InstallationState.RUNNING || it == InstallationState.RESTARTING }

    fun notes(texte: String?): String = Markdown.versHtml(texte)

    fun date(instant: Instant?): String = instant?.let { DATE.format(it) } ?: "jamais"

    /** Colour of a version: stable (green), test for beta / rc (orange), nightly (violet). */
    fun canal(v: Version): String = couleur(cle(v))

    fun canalLabel(v: Version): String = libelle(cle(v))

    private fun cle(v: Version): String = v.prerelease ?: "stable"

    private fun couleur(cle: String): String = when (cle) {
        "stable" -> "stable"
        "nightly" -> "nightly"
        else -> "test"
    }

    private fun libelle(cle: String): String = when (cle) {
        "rc" -> "RC"
        "nightly" -> "Nightly"
        else -> cle.replaceFirstChar { it.uppercase() }
    }

    fun severiteType(type: UpdateType): String = if (type == UpdateType.PATCH) "success" else "warning"

    fun iconeControle(niveau: CheckLevel): String = when (niveau) {
        CheckLevel.OK -> "pi-check-circle"
        CheckLevel.INFO -> "pi-info-circle"
        CheckLevel.WARNING -> "pi-exclamation-triangle"
        CheckLevel.BLOCKING -> "pi-ban"
    }

    fun iconeEtape(etat: StepState): String = when (etat) {
        StepState.PENDING -> "pi-circle"
        StepState.RUNNING -> "pi-spin pi-spinner"
        StepState.OK -> "pi-check"
        StepState.SKIPPED -> "pi-minus"
        StepState.FAILED -> "pi-times"
    }

    fun severiteInstallation(etat: InstallationState): String = when (etat) {
        InstallationState.RUNNING, InstallationState.RESTARTING -> "info"
        InstallationState.DONE -> "success"
        InstallationState.FAILED -> "danger"
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRANCE).withZone(ZoneId.systemDefault())
    }
}

/** A channel filter on the updates page; [couleur] is the tb-canal-* class. */
data class ChipCanal(val cle: String, val libelle: String, val nombre: Int, val couleur: String) : Serializable

/** The rail badge and the banner shown to admins when a version is out. */
@Named
@RequestScoped
class UpdateNoticeBean {

    @Inject
    private lateinit var login: LoginBean

    private val etat: UpdateStatus? by lazy {
        if (login.isAdmin) runCatching { BusinessFactory.getUpdateService().getStatus() }.getOrNull() else null
    }

    val isDisponible: Boolean get() = etat?.updateAvailable == true

    /** « 26.11.0 (fonctionnelle) et 26.10.2 (corrective) » */
    val resume: String
        get() = listOfNotNull(etat?.latestPatch, etat?.latestFeature).joinToString(" et ") {
            "${it.version} (${Libelles.updateType(it.type).lowercase()}${if (it.type == UpdateType.PATCH) ", recommandée" else ""})"
        }
}
