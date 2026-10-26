package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Component
import java.nio.file.Path

/**
 * The program running here (the REST server), as the update system drives it: it switches
 * itself to a new version.
 */
interface LocalComponent {
    val component: Component

    /** null if this program can switch versions by itself here, otherwise why not and what to do. */
    fun blocker(): String?

    /** What happens when it switches, shown before the installation. */
    val switchEffect: String

    /** Version of [directory] this program runs from, null when it runs the files it was deployed with. */
    fun versionInDirectory(directory: UpdateDirectory): String?

    /** Copies the files it runs from into [target] (kept for a manual rollback), when it can. */
    fun keepCurrent(target: Path)

    /** Switches to the files of [directory] (version [tag]): restart, redeploy… Background thread. */
    fun switchTo(directory: Path, tag: String)
}
