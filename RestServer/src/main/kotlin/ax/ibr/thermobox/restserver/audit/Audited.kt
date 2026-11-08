package ax.ibr.thermobox.restserver.audit

/**
 * A change worth the log (Serveur › Journal): once the method answered 2xx, the entry is written
 * by [ax.ibr.thermobox.restserver.filters.AuditFilter] with the user, [action], the target ([target],
 * its id from the path parameter [idParam], else from the answer for a creation) and the JSON
 * body sent ([body]: false for a file, a password).
 *
 * Every method that changes something gets it: a new one without it is simply not logged.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Audited(
    /** « setpoint.set », « rule.create »…: the domain, a dot, the verb (the client words them). */
    val action: String,
    /** element, user, group, channel, rule, measurement, setpoint, import, version; "" for the server itself. */
    val target: String = "",
    val idParam: String = "id",
    val body: Boolean = true,
)
