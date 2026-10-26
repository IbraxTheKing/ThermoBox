package ax.ibr.thermobox.restserver.branding

import ax.ibr.thermobox.business.update.UpdateConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The logo of this ThermoBox server, in this order:
 * 1. the one an ADMIN gave ([setCustom]), kept in `branding/` of the data folder;
 * 2. otherwise the logo of the website the server belongs to ([SiteLogo]);
 * 3. otherwise the ThermoBox logo.
 *
 * Site logos are kept in memory a day (an hour when none was found), so a site is asked at most
 * once a day, and never while answering a client that already has a logo.
 */
class ServerLogo(
    private val directory: Path,
    private val site: (String) -> Logo? = SiteLogo()::fetch,
    private val clock: Clock = Clock.systemUTC(),
) {

    private class Cached(val logo: Logo?, val until: Instant)

    private val sites = ConcurrentHashMap<String, Cached>()
    private val locks = ConcurrentHashMap<String, Any>()

    private val folder: Path get() = directory.resolve("branding")

    /** The logo to show to a client that reached the server at [host]. */
    fun current(host: String?): Logo = custom() ?: SiteLogo.siteOf(host)?.let { siteLogo(it) } ?: DEFAULT_SVG

    /** The ADMIN's logo, null when none was given. */
    fun custom(): Logo? = FILES.firstNotNullOfOrNull { (name, type) ->
        folder.resolve(name).takeIf { Files.isRegularFile(it) }?.let { Logo(Files.readAllBytes(it), type, LogoSource.CUSTOM) }
    }

    /**
     * Saves [bytes] as the server's logo: a PNG, JPEG or GIF (stored as a PNG of at most
     * [LogoImages.MAX_SIDE] pixels, metadata dropped), or an SVG without scripts.
     * @throws IllegalArgumentException if the file is refused, with the reason
     */
    fun setCustom(bytes: ByteArray): Logo {
        require(bytes.isNotEmpty()) { "The file is empty." }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "The logo must not exceed ${MAX_UPLOAD_BYTES / 1024 / 1024} MB." }
        val type = LogoImages.sniff(bytes)
        val (name, content) = when {
            type != null && LogoImages.isRaster(type) -> PNG_FILE to LogoImages.toPng(bytes)
            type == LogoImages.SVG -> { LogoImages.checkSvg(bytes); SVG_FILE to bytes }
            else -> throw IllegalArgumentException("Unrecognized image: the logo must be a PNG, JPEG, GIF or SVG file.")
        }
        Files.createDirectories(folder)
        // Written aside then moved: a reader never sees half a file
        val tmp = folder.resolve("$name.tmp")
        Files.write(tmp, content)
        Files.move(tmp, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        FILES.keys.filter { it != name }.forEach { Files.deleteIfExists(folder.resolve(it)) }
        return custom()!!
    }

    /** Back to the automatic logo (website, then ThermoBox). */
    fun deleteCustom() {
        FILES.keys.forEach { Files.deleteIfExists(folder.resolve(it)) }
    }

    private fun siteLogo(site: String): Logo? {
        val now = clock.instant()
        sites[site]?.takeIf { it.until.isAfter(now) }?.let { return it.logo }
        // One lookup per site at a time: the clients that ask meanwhile wait for its result
        synchronized(locks.computeIfAbsent(site) { Any() }) {
            sites[site]?.takeIf { it.until.isAfter(clock.instant()) }?.let { return it.logo }
            return lookUp(site)
        }
    }

    private fun lookUp(site: String): Logo? {
        val now = clock.instant()
        val logo = runCatching { this.site(site) }.getOrNull()
        if (sites.size >= MAX_SITES) {
            sites.clear()
            locks.clear()
        }
        sites[site] = Cached(logo, now.plus(if (logo != null) FOUND_TTL else MISSING_TTL))
        return logo
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        private const val PNG_FILE = "logo.png"
        private const val SVG_FILE = "logo.svg"
        private val FILES = linkedMapOf(SVG_FILE to LogoImages.SVG, PNG_FILE to LogoImages.PNG)
        private val FOUND_TTL: Duration = Duration.ofDays(1)
        private val MISSING_TTL: Duration = Duration.ofHours(1)
        /** A server is reached under a handful of names; more means someone plays with Host. */
        private const val MAX_SITES = 16

        /** Sizes of the ThermoBox logo as PNG, rendered from its SVG. */
        val DEFAULT_PNG_SIZES = listOf(192, 512, 1024)

        private fun resource(name: String) = requireNotNull(ServerLogo::class.java.getResourceAsStream("/branding/$name")) {
            "Missing resource /branding/$name"
        }.use { it.readBytes() }

        val DEFAULT_SVG: Logo by lazy { Logo(resource("logo.svg"), LogoImages.SVG, LogoSource.DEFAULT) }

        val DEFAULT_PNG: Map<Int, Logo> by lazy {
            DEFAULT_PNG_SIZES.associateWith { Logo(resource("logo-$it.png"), LogoImages.PNG, LogoSource.DEFAULT) }
        }

        /** The one of the server, in the data folder (THERMOBOX_DATA_DIR). */
        val instance: ServerLogo by lazy {
            ServerLogo(runCatching { UpdateConfig.fromEnvironment().directory }.getOrElse { Path.of(System.getProperty("user.home"), ".thermobox") })
        }
    }
}
