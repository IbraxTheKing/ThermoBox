package ax.ibr.thermobox.jsf.config

import jakarta.faces.application.ProjectStage
import jakarta.servlet.ServletContainerInitializer
import jakarta.servlet.ServletContext

/**
 * JSF settings from the environment, applied before Faces starts:
 * - `THERMOBOX_STAGE` : `Production` (default), `Development` (detailed error pages, no caching
 *   of pages), `SystemTest` or `UnitTest`.
 * - `THERMOBOX_HTTPS` : `true` when a reverse proxy serves the application over HTTPS: the
 *   session cookies are then always marked Secure (behind a proxy Tomcat only sees plain HTTP).
 */
object ConfigJsf {

    fun stage(env: Map<String, String> = System.getenv()): ProjectStage {
        val v = env["THERMOBOX_STAGE"]?.trim()?.takeIf { it.isNotEmpty() } ?: return ProjectStage.Production
        return ProjectStage.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
            ?: throw IllegalArgumentException("THERMOBOX_STAGE invalide : $v (${ProjectStage.entries.joinToString()})")
    }

    val https: Boolean by lazy { https(System.getenv()) }

    fun https(env: Map<String, String>): Boolean =
        env["THERMOBOX_HTTPS"]?.trim()?.lowercase() in setOf("true", "1", "oui", "yes", "on")
}

/** Declared in META-INF/services: the container runs it before the Faces listeners. */
class ConfigJsfInitializer : ServletContainerInitializer {
    override fun onStartup(classes: MutableSet<Class<*>>?, ctx: ServletContext) {
        // Keeps a value from web.xml or the server configuration, if any
        ctx.setInitParameter(ProjectStage.PROJECT_STAGE_PARAM_NAME, ConfigJsf.stage().name)
        if (ConfigJsf.https) ctx.sessionCookieConfig.isSecure = true
        ctx.log("ThermoBox : PROJECT_STAGE=${ctx.getInitParameter(ProjectStage.PROJECT_STAGE_PARAM_NAME)}, " +
            "cookies Secure=${if (ConfigJsf.https) "toujours" else "selon la requête"}")
    }
}
