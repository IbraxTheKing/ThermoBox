package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.thermobox.business.security.JwtService
import ax.ibr.thermobox.business.update.BuildInfo
import ax.ibr.thermobox.restserver.update.RestComponent
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.glassfish.jersey.server.ResourceConfig
import java.net.URI

fun main() {

    // Load the JWT config now so a bad THERMOBOX_JWT_SECRET fails at startup, not on the first request.
    val tokenTtl = JwtService.tokenTtl

    val config = ResourceConfig().packages(
        "ax.ibr.thermobox.restserver.resources",
        "ax.ibr.thermobox.restserver.filters",
        "ax.ibr.thermobox.restserver.exceptions"
    )

    // Local development: localhost:8080. In Docker, set THERMOBOX_HTTP_HOST=0.0.0.0, otherwise the
    // server only listens on the container's loopback and is unreachable from outside.
    val host = System.getenv("THERMOBOX_HTTP_HOST")?.takeIf { it.isNotBlank() } ?: "localhost"
    val port = System.getenv("THERMOBOX_HTTP_PORT")?.toIntOrNull() ?: 8080
    val baseUri = URI.create("http://$host:$port/")

    val server = JettyHttpContainerFactory.createServer(
        baseUri,
        config
    )

    // Listen to the boxes
    BusinessFactory.getDriver().listen()

    // Daily check for new versions, and switch when another server sharing the data folder installed one.
    val updates = BusinessFactory.getUpdateService()
    updates.start(RestComponent())

    // Release the database connection pool when the server stops.
    Runtime.getRuntime().addShutdownHook(Thread {
        updates.stop()
        PersistenceFactory.close()
    })

    println("ThermoBox ${BuildInfo.text} - REST server started on $baseUri (JWT lifetime: ${tokenTtl.toMinutes()} min)")

}
