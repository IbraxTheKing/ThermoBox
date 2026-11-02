package ax.ibr.thermobox.business.users.imports

/**
 * The people of a list to import, as read from a file or a database: the names of its columns
 * (its first line) and the cells of each line under them, text, trimmed, "" when empty.
 */
class ImportTable(columns: List<String>, rows: List<List<String>>) {

    /** Names of the columns, made unique and never empty (« Colonne 3 », « Nom (2) »). */
    val columns: List<String> = unique(columns)

    /** The lines, each as long as [columns]; the lines entirely empty are left out. */
    val rows: List<List<String>> = rows
        .map { r -> List(this.columns.size) { i -> r.getOrNull(i)?.trim().orEmpty() } }
        .filter { r -> r.any { it.isNotEmpty() } }

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
