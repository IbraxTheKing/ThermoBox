package ax.ibr.thermobox.persistence.jdbc

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLTransientConnectionException
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * The database reached with plain JDBC, without JPA: the connections of the JDBC data services
 * (`THERMOBOX_DB_ACCESS=jdbc`, see PersistenceFactory).
 *
 * Like the JPA services, each operation takes a connection, runs, and gives it back: the services
 * can be shared by concurrent requests. At most [maxOpen] connections are in use at once (MySQL
 * refuses connections past its max_connections, 151 by default): the other operations wait up to
 * [waitSeconds] for one. Up to [maxIdle] (all of them by default) are kept open for the next
 * operations. A connection idle for more than [checkAfterMillis] is checked before being lent: one
 * that died meanwhile (database restarted, idle timeout) is replaced.
 *
 * Keeping fewer open than may be in use (8 of 20 before 26.12) made a busy server close and open
 * connections all the time, hundreds a second, each with its TCP handshake and MySQL login; checking
 * every connection before each operation cost one more exchange with MySQL each time.
 */
class JdbcDatabase(
    private val url: String,
    private val user: String,
    private val password: String,
    private val maxOpen: Int = 20,
    private val maxIdle: Int = maxOpen,
    private val waitSeconds: Long = 30,
    private val checkAfterMillis: Long = 10_000,
) : AutoCloseable {

    /** A connection given back, and when (System.nanoTime). */
    private class Idle(val cx: Connection, val since: Long)

    private val idle = ArrayBlockingQueue<Idle>(maxOf(1, minOf(maxIdle, maxOpen)))
    private val permits = Semaphore(maxOf(1, maxOpen), true)

    init {
        // Registered by hand: the shaded base.jar may not keep the drivers' META-INF/services
        driverClass(url)?.let { runCatching { Class.forName(it) } }
    }

    /** Runs [block] outside a transaction (each statement is committed on its own). */
    fun <R> read(block: (Connection) -> R): R {
        val cx = borrow()
        var healthy = false
        try {
            val result = block(cx)
            healthy = true
            return result
        } finally {
            giveBack(cx, healthy)
        }
    }

    /** Runs [block] in one transaction: committed on success, rolled back on any exception. */
    fun <R> transaction(block: (Connection) -> R): R {
        val cx = borrow()
        var healthy = false
        try {
            cx.autoCommit = false
            try {
                val result = block(cx)
                cx.commit()
                healthy = true
                return result
            } catch (e: Throwable) {
                runCatching { cx.rollback() }
                throw e
            } finally {
                runCatching { cx.autoCommit = true }
            }
        } finally {
            giveBack(cx, healthy)
        }
    }

    override fun close() {
        while (true) {
            val i = idle.poll() ?: break
            runCatching { i.cx.close() }
        }
    }

    private fun borrow(): Connection {
        if (!permits.tryAcquire(waitSeconds, TimeUnit.SECONDS)) {
            throw SQLTransientConnectionException("All $maxOpen connections to the database stayed busy for $waitSeconds s")
        }
        try {
            while (true) {
                val i = idle.poll() ?: return DriverManager.getConnection(url, user, password)
                // Used a moment ago: still alive; checked only after a while
                val recent = System.nanoTime() - i.since < TimeUnit.MILLISECONDS.toNanos(checkAfterMillis)
                if (recent || runCatching { i.cx.isValid(2) }.getOrDefault(false)) return i.cx
                runCatching { i.cx.close() }
            }
        } catch (e: Throwable) {
            permits.release()
            throw e
        }
    }

    /** Back to the idle ones; closed after an error (its state is unknown) or when enough are idle. */
    private fun giveBack(cx: Connection, healthy: Boolean) {
        try {
            if (!healthy || !idle.offer(Idle(cx, System.nanoTime()))) runCatching { cx.close() }
        } finally {
            permits.release()
        }
    }

    companion object {
        fun driverClass(url: String): String? = when {
            url.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
            url.startsWith("jdbc:mariadb:") -> "org.mariadb.jdbc.Driver"
            else -> null
        }
    }
}

// --- Small helpers shared by the JDBC data services ---

/** Prepares [sql] and binds [params] in order (null, String, Long, Int, Float, LocalDateTime, ByteArray). */
internal fun Connection.statement(sql: String, vararg params: Any?, keys: Boolean = false): PreparedStatement {
    val st = if (keys) prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) else prepareStatement(sql)
    params.forEachIndexed { i, p ->
        val n = i + 1
        when (p) {
            null -> st.setNull(n, Types.NULL)
            is String -> st.setString(n, p)
            is Long -> st.setLong(n, p)
            is Int -> st.setInt(n, p)
            is Float -> st.setFloat(n, p)
            is Double -> st.setDouble(n, p)
            is LocalDateTime -> st.setTimestamp(n, Timestamp.valueOf(p))
            is ByteArray -> st.setBytes(n, p)
            is Boolean -> st.setBoolean(n, p)
            else -> throw IllegalArgumentException("Unsupported parameter type ${p.javaClass.name}")
        }
    }
    return st
}

/** Rows of [sql] mapped by [map], in order. */
internal fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T?): List<T> =
    statement(sql, *params).use { st ->
        st.executeQuery().use { rs ->
            val out = ArrayList<T>()
            while (rs.next()) map(rs)?.let { out += it }
            out
        }
    }

/** Number of rows changed by [sql]. */
internal fun Connection.execute(sql: String, vararg params: Any?): Int = statement(sql, *params).use { it.executeUpdate() }

/** Runs the INSERT [sql] and returns the id the database generated. */
internal fun Connection.insert(sql: String, vararg params: Any?): Long = statement(sql, *params, keys = true).use { st ->
    st.executeUpdate()
    st.generatedKeys.use { rs ->
        check(rs.next()) { "No id generated by: $sql" }
        rs.getLong(1)
    }
}

/** `?, ?, ?` for an IN list of [n] values. */
internal fun placeholders(n: Int): String = List(n) { "?" }.joinToString(", ")

internal fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.floatOrNull(column: String): Float? = getFloat(column).takeUnless { wasNull() }

internal fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

internal fun ResultSet.dateTime(column: String): LocalDateTime? = getTimestamp(column)?.toLocalDateTime()
