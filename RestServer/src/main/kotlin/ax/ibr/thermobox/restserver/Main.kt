package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.glassfish.jersey.server.ResourceConfig
import java.net.URI

fun main() {

    val config = ResourceConfig().packages(
        "ax.ibr.thermobox.restserver.resources",
        "ax.ibr.thermobox.restserver.filters"
    )

    val server = JettyHttpContainerFactory.createServer(
        URI.create("http://localhost:8080/"),
        config
    )

    var simulatedProtocolDriver: ProtocolDriver = SimulatedProtocolDriver()

    simulatedProtocolDriver.listen()


    println("REST server started on http://localhost:8080/")

}