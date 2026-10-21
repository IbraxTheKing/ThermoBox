package ax.ibr.thermobox.jsf.auth

import ax.ibr.thermobox.jsf.config.ConfigJsf
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration

/**
 * Cookie carrying the JWT between the browser and JSF.
 * HttpOnly so scripts can't read the token (XSS), SameSite=Strict so other sites can't send
 * it (CSRF), Secure as soon as the app is served over HTTPS (or always with THERMOBOX_HTTPS=true,
 * behind a reverse proxy).
 */
object AuthCookie {

    const val NAME = "THERMOBOX_TOKEN"

    fun read(req: HttpServletRequest): String? =
        req.cookies?.firstOrNull { it.name == NAME }?.value?.takeIf { it.isNotBlank() }

    fun write(req: HttpServletRequest, res: HttpServletResponse, token: String, maxAge: Duration) {
        res.addCookie(build(req, token, maxAge.seconds.toInt()))
    }

    fun clear(req: HttpServletRequest, res: HttpServletResponse) {
        res.addCookie(build(req, "", 0))
    }

    private fun build(req: HttpServletRequest, value: String, maxAge: Int) = Cookie(NAME, value).apply {
        isHttpOnly = true
        secure = req.isSecure || ConfigJsf.https
        path = req.contextPath.ifEmpty { "/" }
        this.maxAge = maxAge
        setAttribute("SameSite", "Strict")
    }
}
