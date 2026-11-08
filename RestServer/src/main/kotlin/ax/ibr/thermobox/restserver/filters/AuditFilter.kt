package ax.ibr.thermobox.restserver.filters

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.restserver.audit.AuditTargets
import ax.ibr.thermobox.restserver.audit.Audited
import ax.ibr.thermobox.restserver.security.JwtPrincipal
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.container.ResourceInfo
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.Provider
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream

/**
 * Writes the changes made through the API to the log (Serveur › Journal): the methods marked
 * [Audited], once they answered 2xx (a refused change is not one).
 *
 * Before the method runs (after the authentication): the body sent is kept (JSON, 64 KB at most,
 * then given back to Jersey untouched), and so are the name of the target and what the change
 * replaces, read while they still exist. After it: the entry, written aside (AuditLog).
 */
@Provider
@Priority(Priorities.USER)
class AuditFilter : ContainerRequestFilter, ContainerResponseFilter {

    @Context
    private lateinit var resourceInfo: ResourceInfo

    override fun filter(request: ContainerRequestContext) {
        val audited = audited() ?: return
        try {
            before(request, audited)
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            // The change goes on: the log never makes it fail (it will lack what was read here)
            System.err.println("ThermoBox: change not fully logged (${audited.action}): $e")
        }
    }

    private fun before(request: ContainerRequestContext, audited: Audited) {
        if (request.securityContext?.userPrincipal !is JwtPrincipal) return
        if (audited.body && LegacyApi.isJson(request.mediaType) && request.hasEntity()) {
            val original = request.entityStream
            val head = original.readNBytes(MAX_BODY + 1)
            if (head.size <= MAX_BODY) {
                // The old API: its body in French, as the method will read it in English
                val english = if (request.getProperty(LegacyApi.PROPERTY) == true) runCatching { LegacyApi.fromLegacy(head) }.getOrDefault(head) else head
                request.setProperty(BODY, english)
            }
            request.entityStream = SequenceInputStream(ByteArrayInputStream(head), original)
        }
        val id = targetId(request, audited) ?: return
        if (audited.target.isEmpty()) return
        request.setProperty(NAME, AuditTargets.name(audited.target, id))
        request.setProperty(PLACE, AuditTargets.place(audited.target, id))
        request.setProperty(BEFORE, AuditTargets.before(audited.action, audited.target, id))
    }

    override fun filter(request: ContainerRequestContext, response: ContainerResponseContext) {
        if (response.status !in 200..299) return
        val audited = audited() ?: return
        val user = (request.securityContext?.userPrincipal as? JwtPrincipal)?.user ?: return
        try {
            val (answerId, answerName) = AuditTargets.fromAnswer(response.entity)
            val pathId = targetId(request, audited)
            val id = pathId ?: answerId.takeIf { audited.target.isNotEmpty() }
            // A name given in the path (a version installed) when it is not a number
            val pathName = request.uriInfo.pathParameters.getFirst(audited.idParam)?.takeIf { pathId == null && audited.target.isNotEmpty() }
            val name = (request.getProperty(NAME) as? String) ?: answerName?.takeIf { audited.target.isNotEmpty() } ?: pathName
            val place = (request.getProperty(PLACE) as? String) ?: id?.let { AuditTargets.place(audited.target, it) }
            @Suppress("UNCHECKED_CAST")
            val before = (request.getProperty(BEFORE) as? Map<String, Any?>).orEmpty()
            val changes = (request.getProperty(BODY) as? ByteArray)?.let { AuditTargets.sanitized(it) }
            val log = BusinessFactory.getAuditLog()
            log.record(
                AuditEntry(
                    at = log.now(),
                    userId = user.id,
                    username = user.username ?: "?",
                    role = user.type?.name,
                    action = audited.action,
                    targetType = audited.target.ifEmpty { null },
                    targetId = id,
                    targetName = name,
                    details = AuditTargets.details(changes, before, place),
                ),
            )
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            // The change is made: the log never makes it fail (a LinkageError of a library included)
            System.err.println("ThermoBox: change not logged (${audited.action}): $e")
        }
    }

    private fun audited(): Audited? = runCatching { resourceInfo.resourceMethod?.getAnnotation(Audited::class.java) }.getOrNull()

    private fun targetId(request: ContainerRequestContext, audited: Audited): Long? =
        request.uriInfo.pathParameters.getFirst(audited.idParam)?.toLongOrNull()

    private companion object {
        const val MAX_BODY = 64 * 1024
        const val BODY = "thermobox.audit.body"
        const val NAME = "thermobox.audit.name"
        const val PLACE = "thermobox.audit.place"
        const val BEFORE = "thermobox.audit.before"
    }
}
