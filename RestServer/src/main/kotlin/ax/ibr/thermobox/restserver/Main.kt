package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.business.update.BuildInfo
import ax.ibr.thermobox.business.users.FirstAdmin
import ax.ibr.thermobox.restserver.update.RestComponent
import ax.ibr.thermobox.restserver.tls.HttpServers
import ax.ibr.thermobox.restserver.tls.SelfSignedCertificate
import ax.ibr.thermobox.restserver.tls.TlsConfig
import org.glassfish.jersey.message.GZipEncoder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.filter.EncodingFilter

fun main() {

    // Load the JWT config now so a bad THERMOBOX_JWT_SECRET fails at startup, not on the first request.
    val tokenTtl = JwtService.tokenTtl

    val config = ResourceConfig().packages(
        "ax.ibr.thermobox.restserver.resources",
        "ax.ibr.thermobox.restserver.filters",
        "ax.ibr.thermobox.restserver.exceptions"
    )
    // Responses compressed with gzip for the clients that accept it (every browser): the JSON of
    // the curves shrinks about tenfold.
    EncodingFilter.enableFor(config, GZipEncoder::class.java)

    // HTTPS on 8443 (a certificate given, or a self-signed one made in the data folder), HTTP on
    // 8080 redirecting to it; THERMOBOX_HTTPS=false: HTTP only (TlsConfig). In Docker, set
    // THERMOBOX_HTTP_HOST=0.0.0.0, otherwise the server only listens on the container's loopback.
    val tls = TlsConfig.fromEnvironment()
    val http = HttpServers.start(config, tls)
    http.keys?.let { k ->
        println("ThermoBox: HTTPS certificate ${if (k.selfSigned) "self-signed (tls/ of the data folder)" else k.source}, " +
            "for ${SelfSignedCertificate.namesOf(k.certificate).joinToString().ifEmpty { k.certificate.subjectX500Principal.name }}, until ${k.certificate.notAfter}")
        println("ThermoBox: its SHA-256 fingerprint: ${k.fingerprint}")
        if (k.selfSigned) println("ThermoBox: the clients will ask to trust it; give a certificate with THERMOBOX_TLS_CERT / THERMOBOX_TLS_KEY to avoid it")
    }

    // An empty database: its first ADMIN account, with a provisional password (never printed)
    FirstAdmin.ensure()?.let(::println)

    // Listen to the boxes
    BusinessFactory.getDriver().listen()

    // Daily check for new versions, and switch when another server sharing the data folder installed one.
    val updates = BusinessFactory.getUpdateService()
    updates.start(RestComponent())

    // Temperature alerts: the rooms are watched here, whether a client is connected or not
    // (THERMOBOX_ALERTS=false: not by this server).
    val alerts = BusinessFactory.getAlertMonitor()
    alerts.start()

    // The log of the changes (Serveur › Journal): what is older than its retention goes every day
    val audit = BusinessFactory.getAuditLog()
    audit.start()

    // Release the database connection pool when the server stops.
    Runtime.getRuntime().addShutdownHook(Thread {
        alerts.stop()
        http.stop()
        audit.stop()
        updates.stop()
        PersistenceFactory.close()
    })

    println("ThermoBox ${BuildInfo.text} - REST server started on ${http.urls.joinToString(", ")} (JWT lifetime: ${tokenTtl.toMinutes()} min)")

}
