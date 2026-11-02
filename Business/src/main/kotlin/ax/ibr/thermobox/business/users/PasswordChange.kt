package ax.ibr.thermobox.business.users

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.business.security.PasswordPolicy
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.UserService

/**
 * A user replaces their password (POST /api/v2/auth/password): the current one checked, the new
 * one following [PasswordPolicy] and different from the current one (a provisional password can't
 * be kept, nor used again: its hash is replaced). The account is no longer provisional after it.
 */
object PasswordChange {

    sealed interface Result {
        data object Done : Result
        data object WrongCurrent : Result
        /** Codes of [PasswordPolicy.problems], and `same_as_current`. */
        data class Refused(val problems: List<String>) : Result
    }

    fun change(user: User, current: String, new: String, users: UserService = BusinessFactory.getUserService()): Result {
        val stored = user.password
        if (stored == null || !PasswordHasher.isHash(stored) || !PasswordHasher.matches(current, stored)) return Result.WrongCurrent
        val problems = PasswordPolicy.problems(new, user.username).toMutableList()
        if (new == current || (!PasswordHasher.isTooLong(new) && PasswordHasher.matches(new, stored))) problems += "same_as_current"
        if (problems.isNotEmpty()) return Result.Refused(problems)
        user.password = new // hashed by the UserService
        user.passwordChangeRequired = false
        user.provisionalExpiresAt = null
        users.update(user)
        FirstAdmin.forget(user)
        return Result.Done
    }
}
