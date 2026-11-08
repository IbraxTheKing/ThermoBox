package ax.ibr.thermobox.restserver.branding

import ax.ibr.thermobox.business.update.UpdateConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The name an ADMIN gives this ThermoBox server (« Siège social », « Maison »), shown by the
 * clients beside its logo: in the list of accounts, on the login page, in the title of the window
 * and of the notifications. None by default: the clients show its address then.
 *
 * Kept in `branding/name.txt` of the data folder (THERMOBOX_DATA_DIR), with the logo.
 */
class ServerName(private val directory: Path) {

    private val file: Path get() = directory.resolve("branding").resolve("name.txt")

    /** The name, null when none was given. */
    fun get(): String? = runCatching { if (Files.isRegularFile(file)) Files.readString(file).trim() else null }
        .getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Saves [name] (spaces around it removed, spaces inside it collapsed); a blank name removes it.
     * @throws IllegalArgumentException if it is too long or holds control characters
     */
    @Synchronized
    fun set(name: String?): String? {
        val clean = clean(name)
        if (clean == null) {
            Files.deleteIfExists(file)
            return null
        }
        Files.createDirectories(file.parent)
        // Written aside then moved: a reader never sees half a file
        val tmp = file.resolveSibling("name.txt.tmp")
        Files.writeString(tmp, clean)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return clean
    }

    companion object {
        const val MAX_LENGTH = 60

        /** [name] as it is kept, null when blank. */
        fun clean(name: String?): String? {
            val n = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
            require(n.none { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }) { "The name must not contain control characters." }
            require(n.codePointCount(0, n.length) <= MAX_LENGTH) { "The name must not exceed $MAX_LENGTH characters." }
            return n
        }

        /** The one of the server, in the data folder (THERMOBOX_DATA_DIR). */
        val instance: ServerName by lazy {
            ServerName(runCatching { UpdateConfig.fromEnvironment().directory }.getOrElse { Path.of(System.getProperty("user.home"), ".thermobox") })
        }
    }
}
