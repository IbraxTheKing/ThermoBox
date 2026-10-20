package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Version
import java.util.Properties

/** Version this build was made from (thermobox-version.properties, filled in by Maven). */
object BuildInfo {

    /** Tag of the release, or "dev" for a build without -Dthermobox.version. */
    val texte: String by lazy {
        val props = Properties()
        BuildInfo::class.java.getResourceAsStream("/thermobox-version.properties")?.use { props.load(it) }
        props.getProperty("version")?.trim()?.takeUnless { it.isEmpty() || it.startsWith("\${") } ?: "dev"
    }

    val version: Version? by lazy { Version.parse(texte) }
}
