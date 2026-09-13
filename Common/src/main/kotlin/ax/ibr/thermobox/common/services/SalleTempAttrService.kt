package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.services.CrudService
import java.time.Instant
import java.time.LocalDateTime

interface SalleTempAttrService: CrudService<SalleTempAttr> {

    fun getBySalle(salleId: Long): List<SalleTempAttr>?
    fun getBySalle(salle: Salle): List<SalleTempAttr>?
    fun getByTemperature(temperature: Temperature): SalleTempAttr?
    fun getByTemperature(temperatureId: Long): SalleTempAttr?

    fun getCurrentTemperatureFromSalle(salle: Salle, temperatureClass: Temperature): Temperature?
    fun getCurrentTemperaturesFromSalle(salle: Salle): List<Temperature>? // Gets the current temperature + consigne

    fun getConsigneFromSalle(salle: Salle): Consigne?

    fun getTemperaturesFromSalleTimed(salle: Salle, start: Instant, end: Instant): List<Temperature>
    fun getConsignesFromSalleTimed(salle: Salle, start: Instant, end: Instant): List<Consigne>

    fun getAverageTemperatureFromSalle(salle: Salle, temperatureClass: Temperature): Temperature?
    fun getAverageTemperatureFromSalleTimed(salle: Salle, start: Instant, end: Instant): List<Temperature> // First is Temp, second is Consigne

}