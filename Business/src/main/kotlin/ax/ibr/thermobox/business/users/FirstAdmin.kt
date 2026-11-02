package ax.ibr.thermobox.business.users

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.security.ProvisionalPasswords
import ax.ibr.thermobox.business.update.UpdateConfig
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.UserService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * A server started on an empty database gets its first account: an ADMIN, so someone can log in
 * and create (or import) the others.
 *
 * - `THERMOBOX_ADMIN_USERNAME`: its name, `admin` by default (letters only).
 * - `THERMOBOX_ADMIN_PASSWORD`: its password; without it, one is drawn ([ProvisionalPasswords])
 *   and written to `<data folder>/first-admin-password.txt`, readable by the owner only, never to
 *   the console or the logs. Either way it is provisional: to be replaced at the first login (it
 *   doesn't expire: the server may wait a while for its first admin), and the file goes away then.
 */
object FirstAdmin {

    private const val FILE = "first-admin-password.txt"

    /** What was done, for the console (no password in it); null when there were accounts already. */
    fun ensure(
        users: UserService = BusinessFactory.getUserService(),
        env: Map<String, String> = System.getenv(),
        folder: Path = UpdateConfig.fromEnvironment(env).directory,
    ): String? {
        if (users.getAll().isNotEmpty()) return null
        val name = env["THERMOBOX_ADMIN_USERNAME"]?.trim()?.takeIf { it.matches(Regex("^[a-zA-Z]+$")) } ?: "admin"
        val given = env["THERMOBOX_ADMIN_PASSWORD"]?.takeIf { it.isNotEmpty() }
        val admin = User(name, "", UserType.ADMIN)
        val password = ProvisionalPasswords.assign(admin, validity = null)
        if (given != null) admin.password = given
        users.add(admin)
        if (given != null) return "No account yet: ADMIN account '$name' created with THERMOBOX_ADMIN_PASSWORD, to be changed at the first login."
        val file = folder.resolve(FILE)
        Files.createDirectories(folder)
        Files.deleteIfExists(file)
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (e: UnsupportedOperationException) {
            Files.createFile(file) // Windows: the folder of the user is private already
        }
        Files.writeString(file, "ThermoBox — first ADMIN account\nusername: $name\nprovisional password: $password\n\nTo be changed at the first login; this file is deleted then.\n")
        return "No account yet: ADMIN account '$name' created; its provisional password is in $file (to be changed at the first login)."
    }

    /** [user] chose their password: the file of the first password goes. */
    fun forget(user: User, folder: Path = UpdateConfig.fromEnvironment().directory) {
        if (user.type != UserType.ADMIN) return
        runCatching { Files.deleteIfExists(folder.resolve(FILE)) }
    }
}
