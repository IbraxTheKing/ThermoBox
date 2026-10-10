package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import ax.ibr.thermobox.restserver.security.JwtService
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.glassfish.jersey.server.ResourceConfig
import java.net.URI

fun main() {

    // Load the JWT config now so a bad THERMOBOX_JWT_SECRET fails at startup, not on the first request.
    val tokenTtl = JwtService.tokenTtl

    val config = ResourceConfig().packages(
        "ax.ibr.thermobox.restserver.resources",
        "ax.ibr.thermobox.restserver.filters"
    )

    val server = JettyHttpContainerFactory.createServer(
        URI.create("http://localhost:8080/"),
        config
    )

    // Listen to the boxes
    BusinessFactory.getDriver().listen()

    // Release the database connection pool when the server stops.
    Runtime.getRuntime().addShutdownHook(Thread { PersistenceFactory.close() })

    println("REST server started on http://localhost:8080/ (JWT lifetime: ${tokenTtl.toMinutes()} min)")

}