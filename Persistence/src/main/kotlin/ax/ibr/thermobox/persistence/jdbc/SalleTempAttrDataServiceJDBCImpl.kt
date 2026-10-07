package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.SalleTempAttrDataService
import java.time.Instant

class SalleTempAttrDataServiceJDBCImpl : SalleTempAttrDataService {

    override fun getBySalle(salleId: Long): List<SalleTempAttr>? {
        TODO("Not yet implemented")
    }

    override fun getBySalle(salle: Salle): List<SalleTempAttr>? {
        TODO("Not yet implemented")
    }

    override fun getByTemperature(temperature: Temperature): SalleTempAttr? {
        TODO("Not yet implemented")
    }

    override fun getByTemperature(temperatureId: Long): SalleTempAttr? {
        TODO("Not yet implemented")
    }

    override fun getCurrentTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        TODO("Not yet implemented")
    }

    override fun getCurrentTemperaturesFromSalle(salle: Salle): List<Temperature>? {
        TODO("Not yet implemented")
    }

    override fun getConsigneFromSalle(salle: Salle): Consigne? {
        TODO("Not yet implemented")
    }

    override fun getTemperaturesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        TODO("Not yet implemented")
    }

    override fun getConsignesFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Consigne> {
        TODO("Not yet implemented")
    }

    override fun getAverageTemperatureFromSalle(
        salle: Salle,
        temperatureClass: Temperature
    ): Temperature? {
        TODO("Not yet implemented")
    }

    override fun getAverageTemperatureFromSalleTimed(
        salle: Salle,
        start: Instant,
        end: Instant
    ): List<Temperature> {
        TODO("Not yet implemented")
    }

    override fun getAllByTime(
        start: Instant,
        end: Instant
    ): List<SalleTempAttr> {
        TODO("Not yet implemented")
    }

    override fun add(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun update(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun remove(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<SalleTempAttr> {
        TODO("Not yet implemented")
    }

    override fun getById(id: Long): SalleTempAttr? {
        TODO("Not yet implemented")
    }
}