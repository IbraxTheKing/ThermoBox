import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.security.PasswordHasher

/**
 * One-off migration: hashes every password still stored in clear text (accounts created
 * before BCrypt). Accounts also migrate on their next login, this only speeds it up.
 * Run `main` with the same environment variables as the application (THERMOBOX_DB_*).
 */
object HashExistingPasswords {

    @JvmStatic
    fun main(args: Array<String>) {
        val users = BusinessFactory.getUserService()
        val enClair = users.getAll().filter { it.password != null && !PasswordHasher.isHash(it.password) }
        enClair.forEach { user ->
            try {
                users.update(user) // UserService hashes clear-text passwords
                println("Haché : ${user.username}")
            } catch (e: IllegalArgumentException) {
                println("Ignoré : ${user.username} (${e.message}) : changer son mot de passe")
            }
        }
        println("${enClair.size} mot(s) de passe traité(s).")
    }
}
