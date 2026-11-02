package ax.ibr.thermobox.business.security

/**
 * The rules a password chosen by its owner must follow (the one that replaces a provisional
 * password): long enough, letters and figures, not the name of the account, not the provisional
 * one again, and no longer than BCrypt can hash ([PasswordHasher.MAX_BYTES]).
 */
object PasswordPolicy {

    const val MIN_LENGTH = 10

    /** Why [password] is refused (codes the clients translate), empty when it is accepted. */
    fun problems(password: String, username: String?): List<String> {
        val out = mutableListOf<String>()
        if (password.length < MIN_LENGTH) out += "too_short"
        if (PasswordHasher.isTooLong(password)) out += "too_long"
        if (password.none { it.isLetter() }) out += "no_letter"
        if (password.none { it.isDigit() }) out += "no_digit"
        if (password.isBlank() || password.trim() != password) out += "spaces_around"
        val name = username?.trim()?.lowercase().orEmpty()
        if (name.length >= 3 && password.lowercase().contains(name)) out += "contains_username"
        if (password.toSet().size < 4) out += "too_simple"
        return out
    }

    /** The rules, for the clients (GET /api/v2/auth/password-policy). */
    fun describe(): Map<String, Any> = mapOf(
        "minLength" to MIN_LENGTH,
        "maxBytes" to PasswordHasher.MAX_BYTES,
        "letter" to true,
        "digit" to true,
        "notUsername" to true,
    )
}
