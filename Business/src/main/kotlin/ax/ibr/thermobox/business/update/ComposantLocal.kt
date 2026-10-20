package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Composant
import java.nio.file.Path

/**
 * The program running here (REST server or JSF interface), as the update system drives it.
 * Each one switches itself to a new version; the other one follows through the shared folder.
 */
interface ComposantLocal {
    val composant: Composant

    /** null if this program can switch versions by itself here, otherwise why not and what to do. */
    fun empechement(): String?

    /** What happens when it switches, shown before the installation. */
    val effetBascule: String

    /** Version of [dossier] this program runs from, null when it runs the files it was deployed with. */
    fun versionDuDossier(dossier: DossierMisesAJour): String?

    /** Copies the files it runs from into [cible] (kept for a manual rollback), when it can. */
    fun conserverActuels(cible: Path)

    /** Switches to the files of [dossier] (version [tag]): restart, redeploy… Background thread. */
    fun basculer(dossier: Path, tag: String)
}
