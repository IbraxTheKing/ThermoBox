package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Version
import com.fasterxml.jackson.databind.ObjectMapper.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Data folder shared by the REST server and the JSF interface (a Docker volume, or ~/.thermobox):
 *
 * ```
 * updates/<version>/        RestServer.jar, base.jar, JsfClient.war, version.json (SHA-256)
 * updates/current           version to run; the launchers (docker/…/entrypoint.sh) read it
 * updates/installation.json last installation and its steps
 * backups/                  database dumps taken before an update
 * ```
 */
class DossierMisesAJour(val racine: Path) {
    val versions: Path = racine.resolve("updates")
    val sauvegardes: Path = racine.resolve("backups")

    fun preparer() {
        Files.createDirectories(versions)
        Files.createDirectories(sauvegardes)
    }

    fun inscriptible(): Boolean = runCatching {
        preparer()
        val test = Files.createTempFile(versions, ".test", null)
        Files.delete(test)
        true
    }.getOrDefault(false)

    /** [tag] comes from [Version.parse]: digits, letters, dots and dashes, so a safe name. */
    fun dossier(tag: String): Path {
        requireNotNull(Version.parse(tag)) { "Version invalide : $tag" }
        return versions.resolve(tag)
    }

    fun courante(): String? = lire(versions.resolve("current"))

    fun definirCourante(tag: String) = ecrire(versions.resolve("current"), tag)

    /** The three files of [tag] are there and still match the SHA-256 recorded at download. */
    fun complete(tag: String): Boolean = runCatching {
        val dossier = dossier(tag)
        Artefacts.TOUS.all { nom ->
            val f = dossier.resolve(nom)
            Files.isRegularFile(f) && Telechargeur.sha256(f) == sha256(tag, nom)
        }
    }.getOrDefault(false)

    /** SHA-256 of [nom] recorded when [tag] was downloaded. */
    fun sha256(tag: String, nom: String): String? = runCatching {
        json.readTree(dossier(tag).resolve(MANIFESTE).toFile()).path("sha256").path(nom).asText().ifEmpty { null }
    }.getOrNull()

    /** Records the SHA-256 of the files of [dossier]: [complete] checks them before every switch. */
    fun sceller(dossier: Path, tag: String) {
        val racine = json.createObjectNode().put("version", tag)
        val sha = racine.putObject("sha256")
        Artefacts.TOUS.forEach { sha.put(it, Telechargeur.sha256(dossier.resolve(it))) }
        Files.writeString(dossier.resolve(MANIFESTE), json.writerWithDefaultPrettyPrinter().writeValueAsString(racine))
    }

    fun lireInstallation(): ObjectNode? = runCatching {
        json.readTree(versions.resolve(INSTALLATION).toFile()) as? ObjectNode
    }.getOrNull()

    fun ecrireInstallation(installation: ObjectNode) =
        ecrire(versions.resolve(INSTALLATION), json.writerWithDefaultPrettyPrinter().writeValueAsString(installation))

    /**
     * Lock held during an installation: REST and JSF can share the folder, only one of them may
     * install at a time. null if the other one holds it.
     */
    fun verrouiller(): Verrou? {
        preparer()
        val canal = FileChannel.open(versions.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val verrou = runCatching { canal.tryLock() }.getOrNull()
        if (verrou == null) {
            canal.close()
            return null
        }
        return Verrou(canal, verrou)
    }

    class Verrou(private val canal: FileChannel, private val verrou: FileLock) : AutoCloseable {
        override fun close() {
            runCatching { verrou.release() }
            canal.close()
        }
    }

    private fun lire(f: Path): String? = runCatching { Files.readString(f).trim().ifEmpty { null } }.getOrNull()

    /** Write then rename: a reader never sees a half-written file. */
    private fun ecrire(f: Path, contenu: String) {
        preparer()
        val tmp = Files.createTempFile(f.parent, ".tmp", null)
        Files.writeString(tmp, contenu)
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val MANIFESTE = "version.json"
        private const val INSTALLATION = "installation.json"
        val json = ObjectMapper()
    }
}
