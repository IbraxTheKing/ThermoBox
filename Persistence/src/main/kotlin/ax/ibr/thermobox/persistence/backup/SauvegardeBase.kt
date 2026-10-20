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

/** Tables et lignes écrites par [SauvegardeBase.ecrire]. */
data class ResultatSauvegarde(val fichier: Path, val tables: Int, val lignes: Long, val octets: Long)

/**
 * Sauvegarde SQL complète d'une base MySQL, sans mysqldump (absent des images Docker) : schéma
 * (SHOW CREATE TABLE) et données en INSERT, compressés en gzip.
 *
 * Restauration : `gunzip -c fichier.sql.gz | mysql -u <utilisateur> -p <base>`.
 */
class SauvegardeBase(private val url: String, private val utilisateur: String, private val motDePasse: String) {

    fun ecrire(fichier: Path): ResultatSauvegarde {
        Files.createDirectories(fichier.toAbsolutePath().parent)
        // Written next to the target, then renamed: a failed backup never looks complete.
        val partiel = fichier.resolveSibling(fichier.fileName.toString() + ".part")
        var tables = 0
        var lignes = 0L
        try {
            DriverManager.getConnection(url, utilisateur, motDePasse).use { cx ->
                // One InnoDB snapshot for every table: the measures keep arriving during the dump.
                cx.autoCommit = false
                cx.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                cx.isReadOnly = true
                BufferedWriter(OutputStreamWriter(GZIPOutputStream(Files.newOutputStream(partiel)), Charsets.UTF_8)).use { out ->
                    out.write("-- Sauvegarde ThermoBox du ${Instant.now()}\n")
                    out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nSET UNIQUE_CHECKS = 0;\n\n")
                    for (table in tables(cx)) {
                        lignes += table(cx, table, out)
                        tables++
                    }
                    out.write("SET UNIQUE_CHECKS = 1;\nSET FOREIGN_KEY_CHECKS = 1;\n")
                }
                cx.rollback()
            }
            Files.move(partiel, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(partiel)
            throw e
        }
        return ResultatSauvegarde(fichier, tables, lignes, Files.size(fichier))
    }

    private fun tables(cx: Connection): List<String> =
        cx.metaData.getTables(cx.catalog, null, "%", arrayOf("TABLE")).use { rs ->
            buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) }
        }

    private fun table(cx: Connection, table: String, out: BufferedWriter): Long {
        val nom = identifiant(table)
        cx.createStatement().use { st ->
            st.executeQuery("SHOW CREATE TABLE $nom").use { rs ->
                rs.next()
                out.write("DROP TABLE IF EXISTS $nom;\n${rs.getString(2)};\n\n")
            }
        }
        var lignes = 0L
        // Forward-only, read-only, fetch size MIN_VALUE: MySQL Connector/J streams the rows
        // instead of loading the whole table (photos and history can be large).
        cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY).use { st ->
            st.fetchSize = Int.MIN_VALUE
            st.executeQuery("SELECT * FROM $nom").use { rs ->
                val meta = rs.metaData
                val colonnes = (1..meta.columnCount).joinToString(", ") { identifiant(meta.getColumnName(it)) }
                var paquet = 0
                while (rs.next()) {
                    out.write(if (paquet == 0) "INSERT INTO $nom ($colonnes) VALUES\n(" else ",\n(")
                    for (i in 1..meta.columnCount) {
                        if (i > 1) out.write(", ")
                        out.write(valeur(rs, i, meta.getColumnType(i)))
                    }
                    out.write(")")
                    lignes++
                    if (++paquet == LIGNES_PAR_INSERT) {
                        out.write(";\n")
                        paquet = 0
                    }
                }
                if (paquet > 0) out.write(";\n")
            }
        }
        out.write("\n")
        return lignes
    }

    private fun valeur(rs: ResultSet, i: Int, type: Int): String = when (type) {
        Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> octets(rs.getBytes(i))
        Types.BIT, Types.BOOLEAN -> rs.getLong(i).let { if (rs.wasNull()) "NULL" else it.toString() }
        Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.DECIMAL, Types.NUMERIC,
        Types.REAL, Types.FLOAT, Types.DOUBLE -> rs.getString(i) ?: "NULL"
        // Dates and text: MySQL's own text form, as a quoted literal.
        else -> texte(rs.getString(i))
    }

    companion object {
        private const val LIGNES_PAR_INSERT = 200

        fun identifiant(nom: String): String = "`" + nom.replace("`", "``") + "`"

        /** Littéral SQL MySQL (mode par défaut, où \ échappe). */
        fun texte(valeur: String?): String {
            if (valeur == null) return "NULL"
            val sb = StringBuilder(valeur.length + 2).append('\'')
            for (c in valeur) {
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

        fun octets(valeur: ByteArray?): String = when {
            valeur == null -> "NULL"
            valeur.isEmpty() -> "''"
            else -> "X'" + valeur.joinToString("") { "%02x".format(it) } + "'"
        }
    }
}
