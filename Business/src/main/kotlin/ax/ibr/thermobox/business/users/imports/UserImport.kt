package ax.ibr.thermobox.business.users.imports

import ax.ibr.thermobox.business.security.PasswordHasher
import ax.ibr.thermobox.business.security.ProvisionalPasswords
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.UserService
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** What a column of the list can give to an account. */
enum class ImportField { USERNAME, FIRST_NAME, LAST_NAME, EMAIL, ROLE }

/**
 * How to read the list: which column gives each field ([mapping], a field left out is not read),
 * the role of the people whose line gives none ([defaultRole]), how long their provisional
 * passwords last ([validityDays], null: [ProvisionalPasswords.defaultValidity]).
 */
data class ImportOptions(
    val mapping: Map<ImportField, String>,
    val defaultRole: UserType = UserType.VIEWER,
    val validityDays: Int? = null,
) {
    val validity: Duration get() = validityDays?.coerceIn(1, 90)?.let { Duration.ofDays(it.toLong()) } ?: ProvisionalPasswords.defaultValidity
}

/**
 * One line of the list, checked: the account it would give and whether it can be created.
 *
 * [problems]: codes for the clients — `username_missing`, `username_invalid`, `username_taken`
 * (an account has it), `username_repeated` (an earlier line has it), `email_invalid`,
 * `email_taken`, `email_repeated`, `role_unknown`, `name_too_long`. [usernameMade]: the name was
 * made from the given and family names (none given), [usernameAdjusted]: the one given was put in
 * the form of ThermoBox (accents, dots, dashes and spaces taken out).
 */
data class ImportRow(
    val line: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val role: UserType,
    val status: Status,
    val problems: List<String>,
    val usernameMade: Boolean = false,
    val usernameAdjusted: Boolean = false,
) {
    enum class Status { CREATE, DUPLICATE, INVALID }
}

/** An account created by an import, with its provisional password: given once, never kept. */
data class CreatedAccount(
    val line: Int,
    val id: Long?,
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val role: UserType,
    val password: String,
    val expiresAt: LocalDateTime?,
)

/** What an import did: the accounts created, the lines left out, the lines that failed and why. */
data class ImportResult(val created: List<CreatedAccount>, val skipped: List<ImportRow>, val failed: List<Pair<Int, String>>)

/**
 * Import of a list of people (ImportTable: a file, a database) as accounts of ThermoBox:
 *
 * 1. [suggest]: which column probably gives which field (by the names of the columns);
 * 2. [plan]: every line checked against the accounts and the other lines, nothing written;
 * 3. [apply]: the lines that can be created are, each with its provisional password
 *    ([ProvisionalPasswords]: to be changed at the first login, with an expiry); a line that fails
 *    doesn't stop the others.
 */
object UserImport {

    private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s.]+$""")
    private const val MAX_NAME = 100
    private const val MAX_USERNAME = 64

    /** Names of columns that give each field (accents, case, spaces and signs ignored). */
    private val HEADERS: Map<ImportField, List<String>> = mapOf(
        ImportField.USERNAME to listOf("identifiant", "login", "username", "user", "utilisateur", "nomdutilisateur", "compte", "uid", "samaccountname", "pseudo"),
        ImportField.FIRST_NAME to listOf("prenom", "firstname", "givenname", "first", "prenoms"),
        ImportField.LAST_NAME to listOf("nom", "lastname", "surname", "familyname", "nomdefamille", "last", "name", "sn"),
        ImportField.EMAIL to listOf("email", "mail", "courriel", "adressemail", "adresseemail", "emailaddress", "adressecourriel", "mel"),
        ImportField.ROLE to listOf("role", "type", "profil", "droit", "droits", "fonctionthermobox", "acces"),
    )

    /** For each field, the column that most probably gives it (none when nothing fits). */
    fun suggest(columns: List<String>): Map<ImportField, String> {
        val out = mutableMapOf<ImportField, String>()
        val keys = columns.associateWith { key(it) }
        for ((field, names) in HEADERS) {
            // An exact name first, then a name that starts with one (« Adresse e-mail pro »)
            val column = columns.firstOrNull { c -> keys[c] in names && c !in out.values }
                ?: columns.firstOrNull { c -> names.any { n -> n.length >= 4 && keys[c]!!.startsWith(n) } && c !in out.values }
            if (column != null) out[field] = column
        }
        return out
    }

    /** Every line of [table] checked, in order; nothing is written. */
    fun plan(table: ImportTable, options: ImportOptions, users: UserService): List<ImportRow> {
        val existing = users.getAll()
        val takenNames = existing.mapNotNull { it.username?.lowercase() }.toMutableSet()
        val takenEmails = existing.mapNotNull { it.email?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }.toSet()
        val fileNames = mutableSetOf<String>()
        val fileEmails = mutableSetOf<String>()
        val index = options.mapping.mapValues { (_, column) -> table.columns.indexOf(column) }.filterValues { it >= 0 }
        fun cell(row: List<String>, field: ImportField) = index[field]?.let { row[it] }?.trim()?.takeIf { it.isNotEmpty() }

        return table.rows.mapIndexed { i, row ->
            val line = i + 2 // the first line of the list gives the columns
            val problems = mutableListOf<String>()
            var duplicate = false
            val first = cell(row, ImportField.FIRST_NAME)
            val last = cell(row, ImportField.LAST_NAME)
            if ((first?.length ?: 0) > MAX_NAME || (last?.length ?: 0) > MAX_NAME) problems += "name_too_long"

            val email = cell(row, ImportField.EMAIL)
            if (email != null) {
                val e = email.lowercase()
                when {
                    email.length > 255 || !EMAIL.matches(email) -> problems += "email_invalid"
                    e in takenEmails -> { problems += "email_taken"; duplicate = true }
                    e in fileEmails -> { problems += "email_repeated"; duplicate = true }
                }
            }

            val roleText = cell(row, ImportField.ROLE)
            val role = if (roleText == null) options.defaultRole else role(roleText)
            if (role == null) problems += "role_unknown"

            // The name of the account: the one given, in the form of ThermoBox, or one made from
            // the names of the person
            var adjusted = false
            var made = false
            val given = cell(row, ImportField.USERNAME)
            var username: String? = null
            if (given != null) {
                val clean = letters(given)
                when {
                    clean.isEmpty() || clean.length > MAX_USERNAME || !given.all { it.isLetter() || it in ".-_ '" } -> problems += "username_invalid"
                    clean.lowercase() in takenNames -> { problems += "username_taken"; duplicate = true; username = clean }
                    clean.lowercase() in fileNames -> { problems += "username_repeated"; duplicate = true; username = clean }
                    else -> { username = clean; adjusted = clean != given }
                }
            } else {
                username = make(first, last) { it.lowercase() in takenNames || it.lowercase() in fileNames }
                if (username == null) problems += "username_missing" else made = true
            }

            val status = when {
                problems.any { it !in DUPLICATES } -> ImportRow.Status.INVALID
                duplicate -> ImportRow.Status.DUPLICATE
                else -> ImportRow.Status.CREATE
            }
            if (status == ImportRow.Status.CREATE) {
                fileNames += username!!.lowercase()
                email?.let { fileEmails += it.lowercase() }
            }
            ImportRow(line, username, first, last, email, role ?: options.defaultRole, status, problems, made, adjusted)
        }
    }

    private val DUPLICATES = setOf("username_taken", "username_repeated", "email_taken", "email_repeated")

    /**
     * Creates the accounts of the lines that can be (planned again: the accounts may have changed
     * since), each with its provisional password. The hashes (BCrypt, a quarter of a second each)
     * are computed on every processor at once.
     */
    fun apply(table: ImportTable, options: ImportOptions, users: UserService, now: LocalDateTime = LocalDateTime.now()): ImportResult {
        val rows = plan(table, options, users)
        val toCreate = rows.filter { it.status == ImportRow.Status.CREATE }
        val passwords = toCreate.map { ProvisionalPasswords.generate() }
        val hashes = hashAll(passwords)
        val created = mutableListOf<CreatedAccount>()
        val failed = mutableListOf<Pair<Int, String>>()
        val expiresAt = now.plus(options.validity)
        toCreate.forEachIndexed { i, row ->
            try {
                val user = User(row.username!!, hashes[i], row.role).apply {
                    firstName = row.firstName
                    lastName = row.lastName
                    email = row.email
                    passwordChangeRequired = true
                    provisionalExpiresAt = expiresAt
                }
                users.add(user)
                created += CreatedAccount(row.line, user.id, row.username, row.firstName, row.lastName, row.email, row.role, passwords[i], expiresAt)
            } catch (e: Exception) {
                // The reason without the data of the line (it may say who the person is)
                failed += row.line to "create_failed"
            }
        }
        return ImportResult(created, rows.filter { it.status != ImportRow.Status.CREATE }, failed)
    }

    private fun hashAll(passwords: List<String>): List<String> {
        if (passwords.size <= 1) return passwords.map(PasswordHasher::hash)
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(1, 8))
        try {
            return pool.invokeAll(passwords.map { p -> Callable { PasswordHasher.hash(p) } }).map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /** « Administrateur », « gestionnaire », « Lecteur »… → the role; null when unknown. */
    fun role(text: String): UserType? = when (key(text)) {
        "admin", "administrateur", "administratrice", "administrator", "adm" -> UserType.ADMIN
        "manager", "gestionnaire", "gestion", "responsable", "gestionnaires" -> UserType.MANAGER
        "viewer", "lecteur", "lectrice", "lecture", "observateur", "observatrice", "utilisateur", "user", "employe", "employee", "consultation" -> UserType.VIEWER
        else -> UserType.entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
    }

    /**
     * A name of account from the names of the person: « jdupont », then « jeandupont », then
     * « jdupontb », « jdupontc »… until one is free ([taken]); letters only, as ThermoBox wants.
     */
    internal fun make(first: String?, last: String?, taken: (String) -> Boolean): String? {
        val f = letters(first.orEmpty()).lowercase()
        val l = letters(last.orEmpty()).lowercase()
        if (l.isEmpty() && f.isEmpty()) return null
        val candidates = buildList {
            if (f.isNotEmpty() && l.isNotEmpty()) {
                add(f.take(1) + l)
                add(f + l)
            } else add(f + l)
        }.map { it.take(MAX_USERNAME - 2) }
        candidates.firstOrNull { !taken(it) }?.let { return it }
        val base = candidates.first()
        val suffixes = ('a'..'z').map(Char::toString) + ('a'..'z').flatMap { a -> ('a'..'z').map { b -> "$a$b" } }
        return suffixes.map { base + it }.firstOrNull { !taken(it) }
    }

    /** Letters only, without their accents: « Jean-Marie O'Brien » → « JeanMarieOBrien ». */
    internal fun letters(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("æ", "ae").replace("Æ", "AE").replace("œ", "oe").replace("Œ", "OE").replace("ß", "ss")
            .filter { it in 'a'..'z' || it in 'A'..'Z' }

    private fun key(text: String): String = letters(text).lowercase()
}
