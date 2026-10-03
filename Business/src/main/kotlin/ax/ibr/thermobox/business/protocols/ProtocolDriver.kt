package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.business.exceptions.ImpossibleValueException
import ax.ibr.thermobox.business.exceptions.TooHotException
import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.utils.exceptions.DontExistException
import ax.ibr.utils.exceptions.NullException

abstract class ProtocolDriver {

    protected val salleService: SalleService = BusinessFactory().getSalleService()
    protected val temperatureService: TemperatureService = BusinessFactory().getTemperatureService()
    protected val salleTempAttrService: SalleTempAttrService = BusinessFactory().getSalleTempAttrService()

    private val IMPOSSIBLE_TEMPERATURE_VALUE_MAX: Float = 100f
    private val IMPOSSIBLE_TEMPERATURE_VALUE_MIN: Float = -10f
    private val HOT_LIMIT_TEMPERATURE: Float = 50f

    abstract fun listen()

    abstract fun listenToRooms()

    abstract fun listenToTemperatures()

    open fun sendConsigne(s: Salle, c: Consigne) {
        checkConsigne(s, c)
    }

    private fun checkConsigne(s: Salle, c: Consigne) {
        if (s.id == null) {
            throw NullException("Room id is null")
        }
        if (s.id != salleService.getById(s.id!!)?.id) {
            throw DontExistException("Room (id=${s.id}) doesn't exist in database.")
        }
        if (c.value!! >= IMPOSSIBLE_TEMPERATURE_VALUE_MAX || c.value!! <= IMPOSSIBLE_TEMPERATURE_VALUE_MIN) {
            throw ImpossibleValueException("Consigne is impossible to reach: ${c.value} C (min: ${c.value} C, max: ${c.value} C)")
        }
        if (c.value!! >= HOT_LIMIT_TEMPERATURE) {
            throw TooHotException("Consigne is too hot")
        }
    }

}