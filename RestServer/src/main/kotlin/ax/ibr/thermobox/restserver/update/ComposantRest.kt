package ax.ibr.thermobox.restserver.update

import ax.ibr.thermobox.business.update.Artefacts
import ax.ibr.thermobox.business.update.DossierMisesAJour
import ax.ibr.thermobox.business.update.ComposantLocal
import ax.ibr.thermobox.common.update.Composant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * The REST server switches version by stopping with [CODE_REDEMARRAGE]: its launcher
 * (docker/rest/entrypoint.sh, scripts/thermobox-rest.sh) starts it again on updates/current.
 * The launcher sets THERMOBOX_RESTART=auto; without it nobody would restart the server.
 */
class ComposantRest : ComposantLocal {

    override val composant = Composant.REST

    /** RestServer.jar this JVM runs, null when it runs from the sources (IDE, mvn exec). */
    private val jar: Path? = runCatching {
        Path.of(ComposantRest::class.java.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
    }.getOrNull()?.takeIf { it.fileName.toString().endsWith(".jar") }

    override fun empechement(): String? = when {
        jar == null -> "Serveur lancé depuis les sources (IDE ou Maven) : mettez-le à jour à la main."
        System.getenv("THERMOBOX_RESTART") != "auto" ->
            "Ce serveur ne peut pas redémarrer de lui-même : lancez-le avec scripts/thermobox-rest.sh, ou dans Docker, " +
                "qui le relancent sur la nouvelle version."
        else -> null
    }

    override val effetBascule = "Le serveur REST s'arrête puis redémarre sur la nouvelle version (environ 30 secondes d'interruption)."

    override fun versionDuDossier(dossier: DossierMisesAJour): String? {
        val parent = jar?.parent ?: return null
        val versions = dossier.versions.toAbsolutePath().normalize()
        return parent.fileName?.toString()?.takeIf { parent.parent == versions }
    }

    override fun conserverActuels(cible: Path) {
        val dossier = jar?.parent ?: return
        for (nom in listOf(Artefacts.REST, Artefacts.BASE)) {
            val f = cible.resolve(nom)
            if (!Files.exists(f)) Files.copy(dossier.resolve(nom), f, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    override fun basculer(dossier: Path, tag: String) {
        println("[ThermoBox mises à jour] redémarrage sur $tag")
        exitProcess(CODE_REDEMARRAGE)
    }

    companion object {
        /** EX_TEMPFAIL: "stopped to be started again". */
        const val CODE_REDEMARRAGE = 75
    }
}
