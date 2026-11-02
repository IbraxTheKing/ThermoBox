package ax.ibr.thermobox.business.users.imports

import ax.ibr.thermobox.business.update.UpdateConfig
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.util.Properties
import java.util.ServiceLoader

/**
 * Other databases to import people from (a directory of staff, the software of human resources):
 * set by the administrator of the server, never by the page, in a JSON file:
 *
 * ```json
 * [
 *   {
 *     "name": "Annuaire RH",
 *     "url": "jdbc:mysql://rh.example.local:3306/rh",
 *     "user": "thermobox_lecture",
 *     "passwordEnv": "RH_DB_PASSWORD",
 *     "query": "SELECT matricule AS identifiant, prenom, nom, mail FROM employes WHERE actif = 1"
 *   }
 * ]
 * ```
 *
 * - The file: `THERMOBOX_IMPORT_SOURCES`, else `<data folder>/import-sources.json` if it exists.
 * - The password of the database: in the environment variable named by `passwordEnv` (never in the
 *   file, never sent to the clients). Give an account that can only read.
 * - The query gives the columns of the list, as the first line of a file would.
 * - Drivers: MySQL / MariaDB come with the server; others (PostgreSQL, SQL Server, Oracle…): their
 *   JDBC jar in `<data folder>/jdbc-drivers/`.
 *
 * The connection is read-only, the query limited to 30 s and to [ImportTable.MAX_ROWS] lines.
 */
object DatabaseSources {

    data class Source(
        val name: String = "",
        val url: String = "",
        val user: String? = null,
        val passwordEnv: String? = null,
        val query: String = "",
    )

    private val mapper = ObjectMapper()

    fun file(env: Map<String, String> = System.getenv()): Path? {
        env["THERMOBOX_IMPORT_SOURCES"]?.trim()?.takeIf { it.isNotEmpty() }?.let { return Path.of(it) }
        val default = UpdateConfig.fromEnvironment(env).directory.resolve("import-sources.json")
        return default.takeIf { Files.isRegularFile(it) }
    }

    /** The sources set, in the order of the file; none when there is no file. */
    fun all(env: Map<String, String> = System.getenv()): List<Source> {
        val file = file(env) ?: return emptyList()
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            mapper.readTree(file.toFile()).map { n ->
                fun text(key: String) = n.get(key)?.takeIf { it.isTextual }?.asText()
                Source(text("name").orEmpty().trim(), text("url").orEmpty().trim(), text("user"), text("passwordEnv"), text("query").orEmpty())
            }.filter { it.name.isNotBlank() && it.url.startsWith("jdbc:") && it.query.isNotBlank() }
        } catch (e: Exception) {
            System.err.println("ThermoBox: $file can't be read (${e.javaClass.simpleName}): no database to import from")
            emptyList()
        }
    }

    /** The list given by the query of the source [name]. */
    fun read(name: String, env: Map<String, String> = System.getenv()): ImportTable {
        val source = all(env).firstOrNull { it.name == name } ?: throw ImportException("unknown_source", "No such source.")
        val props = Properties()
        source.user?.let { props["user"] = it }
        source.passwordEnv?.let { env[it] }?.let { props["password"] = it }
        val driver = driver(source.url, env) ?: throw ImportException("no_driver", "No JDBC driver for this database.")
        try {
            val cx: Connection = driver.connect(source.url, props) ?: throw ImportException("no_driver", "No JDBC driver for this database.")
            cx.use {
                it.isReadOnly = true
                it.createStatement().use { st ->
                    st.queryTimeout = 30
                    st.maxRows = ImportTable.MAX_ROWS + 1
                    st.fetchSize = 500
                    st.executeQuery(source.query).use { rs ->
                        val meta = rs.metaData
                        val columns = (1..meta.columnCount).map { i -> meta.getColumnLabel(i) ?: meta.getColumnName(i) }
                        if (columns.size > ImportTable.MAX_COLUMNS) throw ImportException("too_many_columns", "More than ${ImportTable.MAX_COLUMNS} columns.")
                        val rows = mutableListOf<List<String>>()
                        while (rs.next()) {
                            rows += columns.indices.map { i -> rs.getString(i + 1).orEmpty() }
                            if (rows.size > ImportTable.MAX_ROWS) throw ImportException("too_many_rows", "More than ${ImportTable.MAX_ROWS} lines.")
                        }
                        return ImportTable(columns, rows)
                    }
                }
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            // Not the message: it may carry the address, the user or a part of the query
            System.err.println("ThermoBox: import from « ${source.name} » failed (${e.javaClass.simpleName})")
            throw ImportException("source_failed", "The database could not be read.")
        }
    }

    /** The driver that takes [url]: those of the server, then those of `jdbc-drivers/`. */
    private fun driver(url: String, env: Map<String, String>): Driver? {
        val builtIn = ServiceLoader.load(Driver::class.java).firstOrNull { runCatching { it.acceptsURL(url) }.getOrDefault(false) }
        if (builtIn != null) return builtIn
        val folder = UpdateConfig.fromEnvironment(env).directory.resolve("jdbc-drivers")
        if (!Files.isDirectory(folder)) return null
        val jars = Files.list(folder).use { s -> s.filter { it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList() }
        if (jars.isEmpty()) return null
        val loader = URLClassLoader(jars.toTypedArray(), DatabaseSources::class.java.classLoader)
        return ServiceLoader.load(Driver::class.java, loader).firstOrNull { runCatching { it.acceptsURL(url) }.getOrDefault(false) }
    }
}
