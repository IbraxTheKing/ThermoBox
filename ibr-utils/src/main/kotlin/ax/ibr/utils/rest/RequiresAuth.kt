package ax.ibr.utils.rest

/**
 * Marks a REST endpoint as requiring authentication.
 *
 * This annotation is used to indicate that a resource method can only be
 * accessed by an authenticated user. Additional authorization rules can be
 * defined using roles or ownership validation.
 *
 * Example:
 * ```
 * @RequiresAuth
 * @GET
 * fun getProfile(): User {
 *     ...
 * }
 * ```
 *
 * By default, any authenticated user is allowed to access the endpoint.
 *
 * ### Role-based authorization
 *
 * You can restrict access to users having at least one of the specified roles:
 *
 * ```
 * @RequiresAuth(roles = ["ADMIN", "MODERATOR"])
 * @DELETE
 * fun deleteUser() {
 *     ...
 * }
 * ```
 *
 * The authentication system is responsible for resolving the current user's
 * roles and validating permissions.
 *
 * ### Owner-based authorization
 *
 * When [allowOwner] is enabled, the endpoint can also be accessed by the user
 * owning the requested resource.
 *
 * Example:
 * ```
 * @RequiresAuth(allowOwner = true, ownerParam = "userId")
 * @PUT
 * fun updateUser(@PathParam("userId") userId: Long) {
 *     ...
 * }
 * ```
 *
 * The framework will compare the authenticated user with the resource owner
 * identified by the parameter specified in [ownerParam].
 *
 * @property roles List of roles allowed to access the endpoint.
 * If empty, no role restriction is applied.
 *
 * @property allowOwner Defines whether the resource owner is allowed to access
 * the endpoint even without having one of the required roles.
 *
 * @property ownerParam Name of the method parameter containing the owner's
 * identifier. Used only when [allowOwner] is enabled.
 *
 * @see roles
 * @see allowOwner
 * @see ownerParam
 *
 * @author ib
 * @since 1.0
 *
 */

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresAuth(
    val roles: Array<String> = [],
    val allowOwner: Boolean = false,
    val ownerParam: String = "id"
)