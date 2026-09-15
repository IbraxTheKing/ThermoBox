package ax.ibr.thermobox.restserver

import ax.ibr.thermobox.business.implementations.SalleServiceImpl
import ax.ibr.thermobox.business.implementations.SalleTempAttrServiceImpl
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.io.mqtt.SimulatedProtocolDriver
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.glassfish.jersey.server.ResourceConfig
import java.net.URI
import java.time.LocalDateTime

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

    simulatedProtocolDriver.sendConsigne(SalleServiceImpl().getById(1)!!,Consigne(15.0f, LocalDateTime.now()))


    println("REST server started on http://localhost:8080/")

    println(SalleServiceImpl().getAll())
    println(SalleTempAttrServiceImpl().getCurrentTemperatureFromSalle(salle = SalleServiceImpl().getById(1)!!, Temperature()))

}