package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable
import java.time.Instant
import java.time.LocalDateTime

class SalleTempAttrServiceImpl : SalleTempAttrService, Serializable {

    private val salleTempAttrService: SalleTempAttrService = PersistenceFactory().getSalleTempAttrDataService()

    override fun getBySalle(salleId: Long): List<SalleTempAttr>? {
        return salleTempAttrService.getBySalle(salleId)
    }

    override fun getBySalle(salle: Salle): List<SalleTempAttr>? {
        return salleTempAttrService.getBySalle(salle)
    }

    override fun getByTemperature(temperature: Temperature): SalleTempAttr? {
        return salleTempAttrService.getByTemperature(temperature)
    }

    override fun getByTemperature(temperatureId: Long): SalleTempAttr? {
        return salleTempAttrService.getByTemperature(temperatureId)
    }

    override fun getCurrentTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        return salleTempAttrService.getCurrentTemperatureFromSalle(salle, temperatureClass)
    }

    override fun getCurrentTemperaturesFromSalle(salle: Salle): List<Temperature>? {
        return salleTempAttrService.getCurrentTemperaturesFromSalle(salle)
    }

    override fun getConsigneFromSalle(salle: Salle): Consigne? {
        return salleTempAttrService.getConsigneFromSalle(salle)
    }

    override fun getTemperaturesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        return salleTempAttrService.getTemperaturesFromSalleTimed(
            salle,
            start,
            end
        )
    }

    override fun getConsignesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Consigne> {
        return salleTempAttrService.getConsignesFromSalleTimed(
            salle,
            start,
            end
        )
    }

    override fun getAverageTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        return salleTempAttrService.getAverageTemperatureFromSalle(salle, temperatureClass)
    }

    override fun getAverageTemperatureFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        return salleTempAttrService.getAverageTemperatureFromSalleTimed(
            salle,
            start,
            end
        )
    }

    override fun getAllByTime(
        start: Instant,
        end: Instant
    ): List<SalleTempAttr> {
        return salleTempAttrService.getAllByTime(start, end)
    }

    override fun add(t: SalleTempAttr) {
        salleTempAttrService.add(t)
    }

    override fun update(t: SalleTempAttr) {
        salleTempAttrService.update(t)
    }

    override fun remove(t: SalleTempAttr) {
        salleTempAttrService.remove(t)
    }

    override fun getAll(): List<SalleTempAttr> {
        return salleTempAttrService.getAll()
    }

    override fun getById(id: Long): SalleTempAttr? {
        return salleTempAttrService.getById(id)
    }

}
