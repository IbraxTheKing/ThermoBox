package ax.ibr.thermobox.persistence.jdbc

import java.sql.Connection
import java.sql.ResultSet

/**
 * CRUD of one table with plain JDBC, the counterpart of `CrudJpaService`, with the same contract:
 * - [add] inserts the entity and gives it the id the database generated;
 * - [update] writes every column of an entity read before (an entity without id is added, as a
 *   JPA merge would);
 * - [remove] deletes its row (the rows that point to it must be gone, as with JPA);
 * - the entities returned are new objects, never shared between two calls.
 *
 * A subclass gives the table, the select list ([select], whose main table is aliased `e`), how a
 * row becomes an entity ([map]) and how an entity is written ([insert], [updateRow]).
 */
abstract class CrudJdbcService<T : Any>(protected val db: JdbcDatabase) {

    /** Table of the entity, quoted if needed. */
    protected abstract val table: String

    /** `SELECT … FROM <table> e [JOIN …]`: every row of the entity, with what [map] needs. */
    protected abstract val select: String

    protected abstract fun map(rs: ResultSet): T?

    protected abstract fun idOf(t: T): Long?

    protected abstract fun setId(t: T, id: Long)

    /** Inserts [t] (whose id is null) and returns the generated id. */
    protected abstract fun insert(cx: Connection, t: T): Long

    /** Writes the columns of [t], which has an id. */
    protected abstract fun updateRow(cx: Connection, t: T)

    /** Deletes [id] and what belongs to it (in the transaction of [cx]). */
    protected open fun deleteRow(cx: Connection, id: Long) {
        cx.execute("DELETE FROM $table WHERE id = ?", id)
    }

    open fun add(t: T) {
        val id = db.transaction { cx -> insert(cx, t) }
        setId(t, id)
    }

    open fun update(t: T) {
        val id = idOf(t) ?: return add(t)
        db.transaction { cx -> updateRow(cx, t.also { setId(it, id) }) }
    }

    open fun remove(t: T) {
        val id = idOf(t) ?: return
        db.transaction { cx -> deleteRow(cx, id) }
    }

    open fun getAll(): List<T> = list("ORDER BY e.id")

    open fun getById(id: Long): T? = list("WHERE e.id = ?", id).firstOrNull()

    /** Entities of `[select] [where]` (a WHERE / ORDER BY / LIMIT clause on alias `e`). */
    protected fun list(where: String, vararg params: Any?): List<T> = db.read { cx -> cx.query("$select $where", *params, map = ::map) }
}
