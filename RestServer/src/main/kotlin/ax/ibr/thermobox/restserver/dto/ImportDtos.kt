package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.business.users.imports.CreatedAccount
import ax.ibr.thermobox.business.users.imports.ImportField
import ax.ibr.thermobox.business.users.imports.ImportOptions
import ax.ibr.thermobox.business.users.imports.ImportRow
import ax.ibr.thermobox.common.entities.UserType

/**
 * A list read, waiting to be checked and confirmed: its columns, its first lines (to show what
 * each column holds), how many lines, and the column suggested for each field.
 */
data class ImportSessionDto(
    val id: String,
    val source: String,
    val columns: List<String>,
    val sample: List<List<String>>,
    val rowCount: Int,
    val suggested: Map<String, String>,
)

/** POST …/check and …/confirm: `{"mapping": {"FIRST_NAME": "Prénom", …}, "defaultRole": "VIEWER", "validityDays": 7}`. */
class ImportOptionsRequest {
    var mapping: Map<String, String>? = null
    var defaultRole: UserType? = null
    var validityDays: Int? = null

    fun options(): ImportOptions = ImportOptions(
        mapping = mapping.orEmpty().mapNotNull { (k, v) ->
            ImportField.entries.firstOrNull { it.name == k }?.let { it to v }
        }.toMap(),
        defaultRole = defaultRole ?: UserType.VIEWER,
        validityDays = validityDays,
    )
}

data class ImportRowDto(
    val line: Int,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val role: UserType,
    val status: String,
    val problems: List<String>,
    val usernameMade: Boolean,
    val usernameAdjusted: Boolean,
) {
    companion object {
        fun of(r: ImportRow) = ImportRowDto(r.line, r.username, r.firstName, r.lastName, r.email, r.role, r.status.name, r.problems, r.usernameMade, r.usernameAdjusted)
    }
}

/** The lines checked, and how many will be created, are duplicates, are invalid. */
data class ImportCheckDto(val rows: List<ImportRowDto>, val create: Int, val duplicates: Int, val invalid: Int)

data class CreatedAccountDto(
    val line: Int,
    val id: Long?,
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val role: UserType,
    val password: String,
    val expiresAt: String?,
) {
    companion object {
        fun of(a: CreatedAccount) = CreatedAccountDto(a.line, a.id, a.username, a.firstName, a.lastName, a.email, a.role, a.password, a.expiresAt?.toString())
    }
}

data class ImportFailureDto(val line: Int, val problem: String)

/**
 * What the import did. [created] carries the provisional passwords in clear: this answer is the
 * only place they exist (the server keeps their hashes only), for the admin to hand them over.
 */
data class ImportResultDto(val created: List<CreatedAccountDto>, val skipped: List<ImportRowDto>, val failed: List<ImportFailureDto>)

data class ImportSourceDto(val name: String)

class ImportSourceRequest {
    var name: String? = null
}
