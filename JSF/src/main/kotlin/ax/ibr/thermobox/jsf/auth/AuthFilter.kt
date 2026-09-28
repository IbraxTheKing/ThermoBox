package ax.ibr.thermobox.jsf.auth

import jakarta.inject.Inject
import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * /app/*   : utilisateur connecté
 * /admin/* : ADMIN uniquement
 */
@WebFilter(urlPatterns = ["/app/*", "/admin/*"])
class AuthFilter : HttpFilter() {

    @Inject
    private lateinit var loginBean: LoginBean

    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        when {
            !loginBean.isLoggedIn ->
                redirect(req, res, "${req.contextPath}/login.xhtml")
            req.servletPath.startsWith("/admin/") && !loginBean.isAdmin ->
                redirect(req, res, "${req.contextPath}/app/index.xhtml")
            else -> chain.doFilter(req, res)
        }
    }

    /** Gère aussi les requêtes AJAX JSF (session expirée pendant un p:poll, par ex.). */
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
**/