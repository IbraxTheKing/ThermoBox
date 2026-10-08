package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import java.time.LocalDateTime


object Services {
    val salles get() = BusinessFactory.getSalleService()
    val salleTemps get() = BusinessFactory.getSalleTempAttrService()
    val temperatures get() = BusinessFactory.getTemperatureService()
    val users get() = BusinessFactory.getUserService()

    val driver: ProtocolDriver get() = BusinessFactory.getDriver()
}

/** Lectures "temps réel" d'une salle, partagées par le tableau de bord et la page détail. */
object SalleData {
    fun latestMesure(salle: Salle): Temperature? = runCatching {
        Services.salleTemps.getCurrentTemperatureFromSalle(salle, Mesurer(0f, LocalDateTime.now()))
    }.getOrNull()

    fun consigne(salle: Salle): Consigne? = runCatching {
        Services.salleTemps.getConsigneFromSalle(salle)
    }.getOrNull()
}

/** Une entité pas encore persistée a un id null (ou 0 selon le mapping). */
fun isNew(id: Long?): Boolean = id == null || id == 0L
