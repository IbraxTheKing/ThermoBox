package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService

abstract class ProtocolDriver {

    protected val salleService: SalleService = BusinessFactory().getSalleService()
    protected val temperatureService: TemperatureService = BusinessFactory().getTemperatureService()
    protected val salleTempAttrService: SalleTempAttrService = BusinessFactory().getSalleTempAttrService()

    abstract fun listen()

    abstract fun listenToRooms()

    abstract fun listenToTemperatures()

    abstract fun sendConsigne(s: Salle, c: Consigne)

}