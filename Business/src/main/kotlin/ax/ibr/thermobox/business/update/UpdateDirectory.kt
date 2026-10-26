package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Data folder of the REST server (a Docker volume, or ~/.thermobox):
 *
 * ```
 * updates/<version>/        RestServer.jar, base.jar, version.json (SHA-256)
 * updates/current           version to run; the launchers (docker/…/entrypoint.sh) read it
 * updates/installation.json last installation and its steps
 * backups/                  database dumps taken before an update
 * ```
 */
class UpdateDirectory(val root: Path) {
    val versions: Path = root.resolve("updates")
    val backups: Path = root.resolve("backups")

    fun prepare() {
        Files.createDirectories(versions)
        Files.createDirectories(backups)
    }

    fun writable(): Boolean = runCatching {
        prepare()
        val test = Files.createTempFile(versions, ".test", null)
        Files.delete(test)
        true
    }.getOrDefault(false)

    /** [tag] comes from [Version.parse]: digits, letters, dots and dashes, so a safe name. */
    fun directory(tag: String): Path {
        requireNotNull(Version.parse(tag)) { "Invalid version: $tag" }
        return versions.resolve(tag)
    }

    fun current(): String? = read(versions.resolve("current"))

    fun setCurrent(tag: String) = write(versions.resolve("current"), tag)

    /** The three files of [tag] are there and still match the SHA-256 recorded at download. */
    fun complete(tag: String): Boolean = runCatching {
        val directory = directory(tag)
        Artifacts.ALL.all { name ->
            val f = directory.resolve(name)
            Files.isRegularFile(f) && Downloader.sha256(f) == sha256(tag, name)
        }
    }.getOrDefault(false)

    /** SHA-256 of [name] recorded when [tag] was downloaded. */
    fun sha256(tag: String, name: String): String? = runCatching {
        json.readTree(directory(tag).resolve(MANIFEST).toFile()).path("sha256").path(name).asText().ifEmpty { null }
    }.getOrNull()

    /** Records the SHA-256 of the files of [directory]: [complete] checks them before every switch. */
    fun seal(directory: Path, tag: String) {
        val root = json.createObjectNode().put("version", tag)
        val sha = root.putObject("sha256")
        Artifacts.ALL.forEach { sha.put(it, Downloader.sha256(directory.resolve(it))) }
        Files.writeString(directory.resolve(MANIFEST), json.writerWithDefaultPrettyPrinter().writeValueAsString(root))
    }

    fun readInstallation(): ObjectNode? = runCatching {
        json.readTree(versions.resolve(INSTALLATION).toFile()) as? ObjectNode
    }.getOrNull()

    fun writeInstallation(installation: ObjectNode) =
        write(versions.resolve(INSTALLATION), json.writerWithDefaultPrettyPrinter().writeValueAsString(installation))

    /**
     * Lock held during an installation: several servers can share the folder, only one of them may
     * install at a time. null if another one holds it.
     */
    fun lock(): Lock? {
        prepare()
        val channel = FileChannel.open(versions.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock == null) {
            channel.close()
            return null
        }
        return Lock(channel, lock)
    }

    class Lock(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
        override fun close() {
            runCatching { lock.release() }
            channel.close()
        }
    }

    private fun read(f: Path): String? = runCatching { Files.readString(f).trim().ifEmpty { null } }.getOrNull()

    /** Write then rename: a reader never sees a half-written file. */
    private fun write(f: Path, content: String) {
        prepare()
        val tmp = Files.createTempFile(f.parent, ".tmp", null)
        Files.writeString(tmp, content)
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val MANIFEST = "version.json"
        private const val INSTALLATION = "installation.json"
        val json = ObjectMapper()
    }
}
