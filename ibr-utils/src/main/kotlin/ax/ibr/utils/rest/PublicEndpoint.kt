package ax.ibr.utils.rest

/**
 * Opts a REST endpoint (or a whole resource class) out of authentication.
 *
 * Every endpoint requires a valid token by default; only use this for endpoints
 * that must be reachable anonymously, such as login or registration.
 *
 * @author ib
 * @since 1.0
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint
