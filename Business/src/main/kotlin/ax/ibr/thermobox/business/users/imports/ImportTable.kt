package ax.ibr.thermobox.business.users.imports

/**
 * The people of a list to import, as read from a file or a database: the names of its columns
 * (its first line) and the cells of each line under them, text, trimmed, "" when empty.
 * [lineNumbers]: the number of each of [rows] in the file (as Excel or a text editor shows it),
 * by default the one after the line of the columns.
 */
class ImportTable(columns: List<String>, rows: List<List<String>>, lineNumbers: List<Int>? = null) {

    /** Names of the columns, made unique and never empty (« Colonne 3 », « Nom (2) »). */
    val columns: List<String> = unique(columns)

    private val kept = rows.indices.filter { i -> rows[i].any { it.isNotBlank() } }

    /** The lines, each as long as [columns]; the lines entirely empty are left out. */
    val rows: List<List<String>> = kept.map { k -> List(this.columns.size) { i -> rows[k].getOrNull(i)?.trim().orEmpty() } }

    /** The number in the file of each of [rows] (an empty line skipped still counts). */
    val lines: List<Int> = kept.map { k -> lineNumbers?.getOrNull(k) ?: (k + 2) }

    companion object {
        /** More lines than this: the list is refused (an import is for people, not logs). */
        const val MAX_ROWS = 20_000
        const val MAX_COLUMNS = 200

        private fun unique(names: List<String>): List<String> {
            val seen = mutableMapOf<String, Int>()
            return names.mapIndexed { i, raw ->
                val base = raw.trim().ifEmpty { "Colonne ${i + 1}" }
                val n = (seen[base.lowercase()] ?: 0) + 1
                seen[base.lowercase()] = n
                if (n == 1) base else "$base ($n)"
            }
        }
    }
}

/** A list that can't be read: [code] for the clients, [message] for people (no cell values in it). */
class ImportException(val code: String, message: String) : Exception(message)
