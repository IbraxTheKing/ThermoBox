package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class TemperatureServiceImpl : TemperatureService {

    private val temperatureService = PersistenceFactory().getTemperatureDataService()

    private val isFahrenheit: Boolean = false

    override fun getByType(temperatureClass: Temperature): List<Temperature>? {
        return temperatureService.getByType(temperatureClass)
    }

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        return temperatureService.getAverage(temperatureClass)
    }

    override fun add(t: Temperature) {
        t.value?.let {
            if (isFahrenheit) {
                // TODO: Faire la section de vérification des températures en Fahrenheit.
            }
            else {
                if (it > 50f) {
                    if (it > 100f){
                        throw ImpossibleValueException("La valeur est impossible OU la maison brule là (${it} °C)")
                    }
                    throw TooHotException("La valeur est beaucoup trop haute [${it} °C]")
                }

                if (it < -10f) {
                    throw ImpossibleValueException("La valeur est impossible OU c'est l'age de glace (${it} °C)")
                }
            }

        }
        temperatureService.add(t)
    }

    override fun update(t: Temperature) {
        temperatureService.update(t)
    }

    override fun remove(t: Temperature) {
        temperatureService.remove(t)
    }

    override fun getAll(): List<Temperature> {
        return temperatureService.getAll()
    }

    override fun getById(id: Long): Temperature? {
        return temperatureService.getById(id)
    }

}