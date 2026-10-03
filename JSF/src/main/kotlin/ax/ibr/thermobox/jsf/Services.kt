package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.business.io.mqtt.SimulatedProtocolDriver
import java.time.LocalDateTime

/**
 * Point d'accès unique aux services métier (même couche que les resources REST).
 * Un `object` évite de stocker les services dans les beans sérialisables (session / vue).
 */
object Services {
    private val factory by lazy { BusinessFactory() }

    val salles by lazy { factory.getSalleService() }
    val salleTemps by lazy { factory.getSalleTempAttrService() }
    val temperatures by lazy { factory.getTemperatureService() }
    val users by lazy { factory.getUserService() }

    val driver: ProtocolDriver by lazy { factory.getDriver() }
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
