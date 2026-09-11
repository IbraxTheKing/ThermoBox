package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.implementations.SalleServiceImpl
import ax.ibr.thermobox.business.implementations.SalleTempAttrServiceImpl
import ax.ibr.thermobox.business.implementations.TemperatureServiceImpl
import ax.ibr.thermobox.business.mqtt.SimulatedProtocolDriver
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

    var simulatedProtocolDriver = SimulatedProtocolDriver(
        salleService = SalleServiceImpl(),
        temperatureService = TemperatureServiceImpl(),
        salleTempAttrService = SalleTempAttrServiceImpl(),
    ).listen()

    println("REST server started on http://localhost:8080/")

}