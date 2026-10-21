package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.business.update.ConfigMisesAJour
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Display settings an ADMIN chooses for every user (Préférences page), kept in
 * `interface.properties` in the data folder (THERMOBOX_DATA_DIR), so they survive restarts and
 * updates without touching the database.
 */
object ReglagesSite {

    /** How the pages look for everyone. */
    enum class Style(val code: String) {
        /** Translucent surfaces, coloured background, blur. */
        VERRE("verre"),
        /** Control panel: flat solid surfaces, no blur nor coloured background, no tint. */
        PANNEAU("panneau");

        companion object {
            fun depuis(code: String?): Style = entries.firstOrNull { it.code == code } ?: VERRE
        }
    }

    private val fichier: Path by lazy {
        runCatching { ConfigMisesAJour.depuisEnvironnement().dossier }.getOrElse { Path.of(System.getProperty("user.home"), ".thermobox") }
            .resolve("interface.properties")
    }

    @Volatile private var cache: Pair<Long, Properties>? = null

    val style: Style get() = Style.depuis(lire().getProperty("style"))

    @Synchronized
    fun changerStyle(style: Style) {
        val p = Properties().apply { putAll(lire()) }
        p.setProperty("style", style.code)
        Files.createDirectories(fichier.parent)
        // Written aside then moved: a reader never sees half a file
        val tmp = fichier.resolveSibling("interface.properties.tmp")
        Files.newBufferedWriter(tmp).use { p.store(it, "ThermoBox : affichage choisi par un administrateur") }
        Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cache = null
    }

    // Re-read only when the file changed (another server sharing the folder may write it)
    private fun lire(): Properties {
        val modif = runCatching { Files.getLastModifiedTime(fichier).toMillis() }.getOrDefault(-1L)
        cache?.let { (date, p) -> if (date == modif) return p }
        val p = Properties()
        if (modif >= 0) runCatching { Files.newBufferedReader(fichier).use { p.load(it) } }
        cache = modif to p
        return p
    }
}
