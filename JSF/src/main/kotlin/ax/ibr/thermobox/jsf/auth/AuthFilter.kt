package ax.ibr.thermobox.jsf.auth

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.common.entities.User
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.time.Instant

/** User authenticated for the current request, with the token that proved it. */
class AuthenticatedUser(val user: User, val token: DecodedJWT) {
    fun hasRole(role: String): Boolean = user.type?.hasRole(role) == true
}

/**
 * Authenticates every page request from the JWT cookie, like the REST SecurityFilter does with
 * the Authorization header. The token is accepted only if it is validly signed, not expired,
 * not revoked (logout) and still points to an existing user. The user is re-read from the
 * database on each request, so a deleted account or a role change applies immediately.
 *
 * Pages under "/app/" need a logged-in user, pages under "/admin/" an ADMIN; the rest (login,
 * registration, resources) is public.
 */
@WebFilter(urlPatterns = ["/*"])
class AuthFilter : HttpFilter() {

    companion object {
        const val CURRENT_USER = "thermobox.auth"
        private const val RESOURCES = "/jakarta.faces.resource/"
    }

    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val path = req.servletPath
        // CSS/JS/images: public, and checking the token for each of them would hit the database for nothing.
        if (path.startsWith(RESOURCES)) return chain.doFilter(req, res)

        val auth = authenticate(req, res)
        if (auth != null) req.setAttribute(CURRENT_USER, auth)

        val isAdminPage = path.startsWith("/admin/")
        val isProtected = isAdminPage || path.startsWith("/app/")
        when {
            isProtected && auth == null -> redirect(req, res, "${req.contextPath}/login.xhtml")
            isAdminPage && auth?.hasRole("ADMIN") != true -> redirect(req, res, "${req.contextPath}/app/index.xhtml")
            else -> {
                // After logout, the back button must not show protected pages from the cache.
                if (isProtected) res.setHeader("Cache-Control", "no-store")
                chain.doFilter(req, res)
            }
        }
    }

    private fun authenticate(req: HttpServletRequest, res: HttpServletResponse): AuthenticatedUser? {
        val token = AuthCookie.read(req) ?: return null
        val jwt = JwtService.verify(token)
        val user = jwt?.subject?.toLongOrNull()?.let { BusinessFactory.getUserService().getById(it) }
        if (jwt == null || user?.type == null) {
            AuthCookie.clear(req, res)
            return null
        }
        renewIfOld(req, res, jwt, user)
        return AuthenticatedUser(user, jwt)
    }

    /**
     * Sliding expiry: an active user gets a fresh token once half of the lifetime has passed.
     * The old token is not revoked, since requests already in flight may still carry it.
     */
    private fun renewIfOld(req: HttpServletRequest, res: HttpServletResponse, jwt: DecodedJWT, user: User) {
        val issuedAt = jwt.issuedAtAsInstant ?: return
        if (Duration.between(issuedAt, Instant.now()) > JwtService.tokenTtl.dividedBy(2)) {
            AuthCookie.write(req, res, JwtService.issue(user).token, JwtService.tokenTtl)
        }
    }

    /** Also handles JSF AJAX requests (e.g. a p:poll after the token expired). */
    private fun redirect(req: HttpServletRequest, res: HttpServletResponse, url: String) {
        if ("partial/ajax" == req.getHeader("Faces-Request")) {
            res.contentType = "text/xml"
            res.characterEncoding = "UTF-8"
            res.writer.print(
                """<?xml version="1.0" encoding="UTF-8"?><partial-response><redirect url="$url"/></partial-response>"""
            )
        } else {
            res.sendRedirect(url)
        }
    }
}
