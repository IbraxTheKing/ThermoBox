package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.restserver.audit.Audited
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.users.imports.DatabaseSources
import ax.ibr.thermobox.business.users.imports.ImportException
import ax.ibr.thermobox.business.users.imports.ImportRow
import ax.ibr.thermobox.business.users.imports.ImportTable
import ax.ibr.thermobox.business.users.imports.TableReaders
import ax.ibr.thermobox.business.users.imports.UserImport
import ax.ibr.thermobox.restserver.dto.CreatedAccountDto
import ax.ibr.thermobox.restserver.dto.ImportCheckDto
import ax.ibr.thermobox.restserver.dto.ImportFailureDto
import ax.ibr.thermobox.restserver.dto.ImportOptionsRequest
import ax.ibr.thermobox.restserver.dto.ImportResultDto
import ax.ibr.thermobox.restserver.dto.ImportRowDto
import ax.ibr.thermobox.restserver.dto.ImportSessionDto
import ax.ibr.thermobox.restserver.dto.ImportSourceDto
import ax.ibr.thermobox.restserver.dto.ImportSourceRequest
import ax.ibr.thermobox.restserver.imports.ImportSessions
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.io.InputStream

/**
 * Import of accounts from a list of people (ADMIN only):
 *
 * 1. the list: POST /file?name=personnel.xlsx (the bytes of a .xlsx or .csv file as the body) or
 *    POST /source {"name": …} (a database set on the server, GET /sources) → its columns, its
 *    first lines and the column suggested for each field, under an id;
 * 2. POST /{id}/check with the columns chosen → every line: created, duplicate or invalid, why;
 * 3. POST /{id}/confirm, same body → the accounts created with their provisional passwords (the
 *    only time they are given), the lines left out, the lines that failed.
 *
 * The lists stay in memory only, half an hour at most (ImportSessions). Nothing of their content
 * goes to the logs.
 */
@Path("api/v2/users/import")
@Produces(MediaType.APPLICATION_JSON)
class UserImportResource {

    private val users = BusinessFactory.getUserService()

    @GET
    @Path("/sources")
    @RequiresRole("ADMIN")
    fun sources(): List<ImportSourceDto> = DatabaseSources.all().map { ImportSourceDto(it.name) }

    @POST
    @Path("/file")
    @RequiresRole("ADMIN")
    @Consumes(MediaType.WILDCARD)
    fun file(body: InputStream?, @QueryParam("name") name: String?, @Context security: SecurityContext): Response {
        val bytes = try {
            body?.let { readAtMost(it, TableReaders.MAX_BYTES) } ?: return error(Response.Status.BAD_REQUEST, "empty", "Missing file")
        } catch (e: ImportException) {
            return error(Response.Status.REQUEST_ENTITY_TOO_LARGE, e.code, e.message ?: "The file is too large.")
        }
        val fileName = name?.substringAfterLast('/')?.substringAfterLast('\\')?.take(200).orEmpty().ifEmpty { "liste" }
        return read(security) { fileName to TableReaders.read(fileName, bytes) }
    }

    @POST
    @Path("/source")
    @RequiresRole("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    fun source(request: ImportSourceRequest?, @Context security: SecurityContext): Response {
        val name = request?.name?.trim().orEmpty()
        if (name.isEmpty()) return error(Response.Status.BAD_REQUEST, "unknown_source", "Missing source name")
        return read(security) { name to DatabaseSources.read(name) }
    }

    @POST
    @Path("/{id}/check")
    @RequiresRole("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    fun check(@PathParam("id") id: String, request: ImportOptionsRequest?, @Context security: SecurityContext): Response {
        val session = ImportSessions.get(id, security.currentUser.id!!) ?: return expired()
        val rows = UserImport.plan(session.table, (request ?: ImportOptionsRequest()).options(), users)
        return Response.ok(
            ImportCheckDto(
                rows.map(ImportRowDto::of),
                rows.count { it.status == ImportRow.Status.CREATE },
                rows.count { it.status == ImportRow.Status.DUPLICATE },
                rows.count { it.status == ImportRow.Status.INVALID },
            )
        ).build()
    }

    @POST
    @Path("/{id}/confirm")
    @RequiresRole("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Audited("users.import")
    fun confirm(@PathParam("id") id: String, request: ImportOptionsRequest?, @Context security: SecurityContext): Response {
        val session = ImportSessions.get(id, security.currentUser.id!!) ?: return expired()
        // Once: a second confirmation would find the accounts taken anyway
        ImportSessions.close(id)
        val result = UserImport.apply(session.table, (request ?: ImportOptionsRequest()).options(), users)
        println("ThermoBox: import by ${security.currentUser.username}: ${result.created.size} account(s) created, ${result.skipped.size} line(s) left out, ${result.failed.size} failed")
        return Response.ok(
            ImportResultDto(
                result.created.map(CreatedAccountDto::of),
                result.skipped.map(ImportRowDto::of),
                result.failed.map { (line, problem) -> ImportFailureDto(line, problem) },
            )
        ).header("Cache-Control", "no-store").build()
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun cancel(@PathParam("id") id: String, @Context security: SecurityContext): Response {
        ImportSessions.get(id, security.currentUser.id!!)?.let { ImportSessions.close(it.id) }
        return Response.noContent().build()
    }

    private fun read(security: SecurityContext, block: () -> Pair<String, ImportTable>): Response = try {
        val (source, table) = block()
        if (table.rows.isEmpty()) error(Response.Status.BAD_REQUEST, "empty", "No line under the names of the columns")
        else {
            val session = ImportSessions.open(security.currentUser.id!!, source, table)
            Response.ok(
                ImportSessionDto(
                    session.id, source, table.columns, table.rows.take(5), table.rows.size,
                    UserImport.suggest(table.columns).mapKeys { it.key.name },
                )
            ).build()
        }
    } catch (e: ImportException) {
        error(if (e.code == "too_large") Response.Status.REQUEST_ENTITY_TOO_LARGE else Response.Status.BAD_REQUEST, e.code, e.message ?: "Unreadable list")
    }

    /** The body, refused beyond [limit] bytes without reading the rest. */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw ImportException("too_large", "The file is too large.")
        }
        return out.toByteArray()
    }

    private fun expired(): Response = error(Response.Status.NOT_FOUND, "import_expired", "This import is no longer open: read the list again")

    private fun error(status: Response.Status, code: String, message: String): Response =
        Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(mapOf("error" to message, "code" to code)).build()
}
