package ax.ibr.thermobox.persistence.backup

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.util.zip.GZIPOutputStream

/** Tables and rows written by [DatabaseBackup.write]. */
data class BackupResult(val file: Path, val tables: Int, val lines: Long, val bytes: Long)

/**
 * Full SQL backup of a MySQL database, without mysqldump (missing from the Docker images): schema
 * (SHOW CREATE TABLE) and data as INSERTs, gzip-compressed.
 *
 * Restore: `gunzip -c file.sql.gz | mysql -u <user> -p <database>`.
 */
class DatabaseBackup(private val url: String, private val user: String, private val password: String) {

    fun write(file: Path): BackupResult {
        Files.createDirectories(file.toAbsolutePath().parent)
        // Written next to the target, then renamed: a failed backup never looks complete.
        val partial = file.resolveSibling(file.fileName.toString() + ".part")
        var tables = 0
        var lines = 0L
        try {
            DriverManager.getConnection(url, user, password).use { cx ->
                // One InnoDB snapshot for every table: the measures keep arriving during the dump.
                cx.autoCommit = false
                cx.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                cx.isReadOnly = true
                BufferedWriter(OutputStreamWriter(GZIPOutputStream(Files.newOutputStream(partial)), Charsets.UTF_8)).use { out ->
                    out.write("-- ThermoBox backup of ${Instant.now()}\n")
                    out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nSET UNIQUE_CHECKS = 0;\n\n")
                    for (table in tables(cx)) {
                        lines += table(cx, table, out)
                        tables++
                    }
                    out.write("SET UNIQUE_CHECKS = 1;\nSET FOREIGN_KEY_CHECKS = 1;\n")
                }
                cx.rollback()
            }
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(partial)
            throw e
        }
        return BackupResult(file, tables, lines, Files.size(file))
    }

    private fun tables(cx: Connection): List<String> =
        cx.metaData.getTables(cx.catalog, null, "%", arrayOf("TABLE")).use { rs ->
            buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) }
        }

    private fun table(cx: Connection, table: String, out: BufferedWriter): Long {
        val name = identifier(table)
        cx.createStatement().use { st ->
            st.executeQuery("SHOW CREATE TABLE $name").use { rs ->
                rs.next()
                out.write("DROP TABLE IF EXISTS $name;\n${rs.getString(2)};\n\n")
            }
        }
        var lines = 0L
        // Forward-only, read-only, fetch size MIN_VALUE: MySQL Connector/J streams the rows
        // instead of loading the whole table (photos and history can be large).
        cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY).use { st ->
            st.fetchSize = Int.MIN_VALUE
            st.executeQuery("SELECT * FROM $name").use { rs ->
                val meta = rs.metaData
                val columns = (1..meta.columnCount).joinToString(", ") { identifier(meta.getColumnName(it)) }
                var batch = 0
                while (rs.next()) {
                    out.write(if (batch == 0) "INSERT INTO $name ($columns) VALUES\n(" else ",\n(")
                    for (i in 1..meta.columnCount) {
                        if (i > 1) out.write(", ")
                        out.write(value(rs, i, meta.getColumnType(i)))
                    }
                    out.write(")")
                    lines++
                    if (++batch == ROWS_PER_INSERT) {
                        out.write(";\n")
                        batch = 0
                    }
                }
                if (batch > 0) out.write(";\n")
            }
        }
        out.write("\n")
        return lines
    }

    private fun value(rs: ResultSet, i: Int, type: Int): String = when (type) {
        Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> bytes(rs.getBytes(i))
        Types.BIT, Types.BOOLEAN -> rs.getLong(i).let { if (rs.wasNull()) "NULL" else it.toString() }
        Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.DECIMAL, Types.NUMERIC,
        Types.REAL, Types.FLOAT, Types.DOUBLE -> rs.getString(i) ?: "NULL"
        // Dates and text: MySQL's own text form, as a quoted literal.
        else -> text(rs.getString(i))
    }

    companion object {
        private const val ROWS_PER_INSERT = 200

        fun identifier(name: String): String = "`" + name.replace("`", "``") + "`"

        /** MySQL SQL literal (default mode, where \ escapes). */
        fun text(value: String?): String {
            if (value == null) return "NULL"
            val sb = StringBuilder(value.length + 2).append('\'')
            for (c in value) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '\'' -> sb.append("\\'")
                    '\u0000' -> sb.append("\\0")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\u001a' -> sb.append("\\Z")
                    else -> sb.append(c)
                }
            }
            return sb.append('\'').toString()
        }

        fun bytes(value: ByteArray?): String = when {
            value == null -> "NULL"
            value.isEmpty() -> "''"
            else -> "X'" + value.joinToString("") { "%02x".format(it) } + "'"
        }
    }
}
