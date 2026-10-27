package ax.ibr.thermobox.jsf.update

import ax.ibr.thermobox.business.update.Artefacts
import ax.ibr.thermobox.business.update.ComposantLocal
import ax.ibr.thermobox.business.update.DossierMisesAJour
import ax.ibr.thermobox.business.update.Telechargeur
import ax.ibr.thermobox.common.update.Composant
import jakarta.servlet.ServletContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The JSF interface switches version by replacing the WAR that Tomcat deploys: Tomcat notices
 * the new file and redeploys the application (autoDeploy, on by default). The WAR is
 * THERMOBOX_WAR_PATH, or the one next to the unpacked application (webapps/ROOT → ROOT.war).
 * In Docker, docker/jsf-tomcat/entrypoint.sh puts updates/current back at every start.
 */
class ComposantJsf(contexte: ServletContext) : ComposantLocal {

    override val composant = Composant.JSF

    private val serveur: String = contexte.serverInfo.orEmpty()

    private val war: Path? = System.getenv("THERMOBOX_WAR_PATH")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        ?: contexte.getRealPath("/")?.let { Path.of(it).toAbsolutePath().normalize() }
            ?.let { it.resolveSibling(it.fileName.toString() + ".war") }
            ?.takeIf { Files.isRegularFile(it) }

    override fun empechement(): String? = when {
        // WildFly needs a WAR without Mojarra / Weld (docker/jsf-wildfly/prepare-war.sh).
        serveur.contains("WildFly", true) || serveur.contains("JBoss", true) || serveur.contains("Undertow", true) ->
            "Sur WildFly, le WAR doit être adapté (docker/jsf-wildfly/prepare-war.sh) : mettez cette interface à jour à la main."
        war == null -> "Fichier .war déployé introuvable : indiquez-le dans THERMOBOX_WAR_PATH (le .war que Tomcat surveille)."
        !Files.isWritable(war) || !Files.isWritable(war.parent) -> "$war n'est pas modifiable par le serveur d'applications."
        else -> null
    }

    override val effetBascule =
        "L'interface JSF est redéployée par Tomcat (quelques secondes) : les pages ouvertes devront être rechargées."

    override fun versionDuDossier(dossier: DossierMisesAJour): String? {
        val sha = shaDuWar() ?: return null
        val versions = runCatching { Files.list(dossier.versions).use { l -> l.filter { Files.isDirectory(it) }.toList() } }.getOrDefault(emptyList())
        // The current version first: it's the usual answer.
        val courante = dossier.courante()
        return versions.map { it.fileName.toString() }.sortedByDescending { it == courante }
            .firstOrNull { runCatching { dossier.sha256(it, Artefacts.WAR) }.getOrNull() == sha }
    }

    override fun conserverActuels(cible: Path) {
        val w = war ?: return
        val f = cible.resolve(Artefacts.WAR)
        if (!Files.exists(f)) Files.copy(w, f)
    }

    override fun basculer(dossier: Path, tag: String) {
        val w = war ?: error("WAR introuvable")
        // Copied next to it, then renamed: Tomcat never sees a half-written WAR.
        val tmp = w.resolveSibling(".${w.fileName}.$tag.tmp")
        Files.copy(dossier.resolve(Artefacts.WAR), tmp, StandardCopyOption.REPLACE_EXISTING)
        Files.move(tmp, w, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        println("[ThermoBox mises à jour] $w remplacé par la version $tag : Tomcat redéploie l'interface")
    }

    // The WAR is ~25 MB: hashed again only when it changes.
    @Volatile private var empreinte: Triple<Long, Long, String>? = null

    private fun shaDuWar(): String? {
        val w = war ?: return null
        return runCatching {
            val taille = Files.size(w)
            val date = Files.getLastModifiedTime(w).toMillis()
            empreinte?.takeIf { it.first == taille && it.second == date }?.third
                ?: Telechargeur.sha256(w).also { empreinte = Triple(taille, date, it) }
        }.getOrNull()
    }
}
