package ax.ibr.utils.rest

/**
 * Marks a REST endpoint as requiring a specific user role.
 *
 * This annotation is used to restrict access to a resource method by checking
 * whether the authenticated user has the required role.
 *
 * Example:
 * ```
 * @RequiresRole("ADMIN")
 * @DELETE
 * fun deleteUser(@PathParam("id") id: Long) {
 *     ...
 * }
 * ```
 *
 * The security layer is responsible for retrieving the current authenticated
 * user and validating that the user owns the required role.
 *
 * This annotation can be used alone or combined with other security
 * annotations depending on the authorization strategy.
 *
 * Example with multiple restrictions:
 * ```
 * @RequiresAuth
 * @RequiresRole("MODERATOR")
 * @PUT
 * fun updatePost() {
 *     ...
 * }
 * ```
 *
 * @property value The name of the role required to access the endpoint.
 *
 * @see RequiresAuth
 *
 * @author ib
 * @since 1.0
 *
 */

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresRole(val value: String) {
}