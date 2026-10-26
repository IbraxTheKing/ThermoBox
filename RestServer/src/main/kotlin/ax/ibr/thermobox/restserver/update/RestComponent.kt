package ax.ibr.thermobox.restserver.update

import ax.ibr.thermobox.business.update.Artifacts
import ax.ibr.thermobox.business.update.UpdateDirectory
import ax.ibr.thermobox.business.update.LocalComponent
import ax.ibr.thermobox.common.update.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * The REST server switches version by stopping with [RESTART_EXIT_CODE]: its launcher
 * (docker/rest/entrypoint.sh, scripts/thermobox-rest.sh) starts it again on updates/current.
 * The launcher sets THERMOBOX_RESTART=auto; without it nobody would restart the server.
 */
class RestComponent : LocalComponent {

    override val component = Component.REST

    /** RestServer.jar this JVM runs, null when it runs from the sources (IDE, mvn exec). */
    private val jar: Path? = runCatching {
        Path.of(RestComponent::class.java.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
    }.getOrNull()?.takeIf { it.fileName.toString().endsWith(".jar") }

    override fun blocker(): String? = when {
        jar == null -> "Server started from the sources (IDE or Maven): update it by hand."
        System.getenv("THERMOBOX_RESTART") != "auto" ->
            "This server cannot restart by itself: start it with scripts/thermobox-rest.sh, or in Docker, " +
                "which restart it on the new version."
        else -> null
    }

    override val switchEffect = "The REST server stops, then restarts on the new version (about 30 seconds of downtime)."

    override fun versionInDirectory(directory: UpdateDirectory): String? {
        val parent = jar?.parent ?: return null
        val versions = directory.versions.toAbsolutePath().normalize()
        return parent.fileName?.toString()?.takeIf { parent.parent == versions }
    }

    override fun keepCurrent(target: Path) {
        val directory = jar?.parent ?: return
        for (name in listOf(Artifacts.REST, Artifacts.BASE)) {
            val f = target.resolve(name)
            if (!Files.exists(f)) Files.copy(directory.resolve(name), f, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    override fun switchTo(directory: Path, tag: String) {
        println("[ThermoBox updates] restarting on $tag")
        exitProcess(RESTART_EXIT_CODE)
    }

    companion object {
        /** EX_TEMPFAIL: "stopped to be started again". */
        const val RESTART_EXIT_CODE = 75
    }
}
