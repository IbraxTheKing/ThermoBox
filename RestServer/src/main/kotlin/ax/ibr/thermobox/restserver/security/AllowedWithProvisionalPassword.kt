package ax.ibr.thermobox.restserver.security

/**
 * An endpoint a user with a provisional password may call (their account, logging out, choosing
 * their password). All the others answer 403 `password_change_required` until they have chosen
 * one ([ax.ibr.thermobox.restserver.filters.SecurityFilter]).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowedWithProvisionalPassword
