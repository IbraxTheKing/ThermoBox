package ax.ibr.thermobox.restserver.resources

import jakarta.ws.rs.core.Response

/**
 * `?limit=&offset=` of the lists that can hold millions of rows (every temperature of the base):
 * the latest first, [DEFAULT_LIMIT] by default, [MAX_LIMIT] at most. The response stays a JSON
 * array; `X-Total-Count`, with the first page, gives the size of the whole list when it is known.
 */
internal data class Page(val offset: Int, val limit: Int) {
    companion object {
        const val DEFAULT_LIMIT = 1000
        const val MAX_LIMIT = 10_000
        const val TOTAL_HEADER = "X-Total-Count"

        /** null if [limit] or [offset] is out of range. */
        fun of(limit: Int?, offset: Int?): Page? {
            val l = limit ?: DEFAULT_LIMIT
            val o = offset ?: 0
            return if (l in 1..MAX_LIMIT && o >= 0) Page(o, l) else null
        }

        fun invalid(): Response = Response.status(Response.Status.BAD_REQUEST)
            .entity(mapOf("error" to "limit must be between 1 and $MAX_LIMIT, offset at least 0")).build()
    }
}
