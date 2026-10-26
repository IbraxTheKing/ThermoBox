package ax.ibr.thermobox.jsf.media

import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.jsf.Services
import ax.ibr.thermobox.jsf.auth.AuthFilter
import ax.ibr.thermobox.jsf.auth.AuthenticatedUser
import jakarta.servlet.annotation.WebServlet
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Serves the photo of a salle: `/media/salle/{id}?v={version}`, for logged-in users only
 * ([AuthFilter] has already checked the JWT cookie). Links carry the image date as version, so
 * the browser can keep an image for long: a new photo comes with a new URL.
 */
@WebServlet(urlPatterns = ["${SalleImageServlet.PATH}/*"])
class SalleImageServlet : HttpServlet() {

    override fun doGet(req: HttpServletRequest, res: HttpServletResponse) {
        val auth = req.getAttribute(AuthFilter.CURRENT_USER) as? AuthenticatedUser
            ?: return res.sendError(HttpServletResponse.SC_UNAUTHORIZED)

        val id = req.pathInfo?.removePrefix("/")?.toLongOrNull()
            ?: return res.sendError(HttpServletResponse.SC_NOT_FOUND)
        // A photo outside the user's accesses answers like a missing one.
        if (!auth.hasRole("ADMIN") && !Services.acces.scope(auth.user).canSee(id)) {
            return res.sendError(HttpServletResponse.SC_NOT_FOUND)
        }
        val image = Services.images.getByRoom(Room().apply { this.id = id })
            ?: return res.sendError(HttpServletResponse.SC_NOT_FOUND)

        val etag = "\"${version(image.updated)}\""
        res.setHeader("ETag", etag)
        // Not "immutable": a withdrawn access must not leave the photo in the browser cache for a year.
        res.setHeader("Cache-Control", "private, no-cache")
        if (req.getHeader("If-None-Match") == etag) return res.setStatus(HttpServletResponse.SC_NOT_MODIFIED)

        res.contentType = image.contentType
        // Never interpreted as anything but an image, even if opened directly.
        res.setHeader("X-Content-Type-Options", "nosniff")
        res.setHeader("Content-Security-Policy", "default-src 'none'")
        res.setContentLength(image.data.size)
        res.outputStream.write(image.data)
    }

    companion object {
        const val PATH = "/media/salle"

        /** Link to the photo of salle [id], relative to the application. */
        fun url(id: Long, updated: LocalDateTime): String = "$PATH/$id?v=${version(updated)}"

        private fun version(updated: LocalDateTime): Long =
            updated.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
