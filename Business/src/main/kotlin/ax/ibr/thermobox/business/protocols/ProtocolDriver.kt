package ax.ibr.thermobox.business.protocols

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.rules.ConsigneRules
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import ax.ibr.utils.exceptions.DontExistException
import ax.ibr.utils.exceptions.NullException

abstract class ProtocolDriver {

    protected val salleService: SalleService = BusinessFactory.getSalleService()
    protected val temperatureService: TemperatureService = BusinessFactory.getTemperatureService()
    protected val salleTempAttrService: SalleTempAttrService = BusinessFactory.getSalleTempAttrService()

    abstract fun listen()

    abstract fun listenToRooms()

    abstract fun listenToTemperatures()

    open fun sendConsigne(s: Salle, c: Consigne) {
        checkConsigne(s, c)
    }

    /** Boxes known in database (measures only ever go to a box). */
    protected fun getBoitiers(): List<Boitier> =
        salleService.getAllByType(Boitier(null)).filterIsInstance<Boitier>()

    /** Saves a box that announced itself, unless a box with that name already exists. */
    protected fun registerBoitier(name: String): Boolean {
        if (name.isBlank() || getBoitiers().any { it.name == name }) return false
        salleService.add(Boitier(name))
        return true
    }

    private fun checkConsigne(s: Salle, c: Consigne) {
        if (s.id == null) {
            throw NullException("Room id is null")
        }
        if (s.id != salleService.getById(s.id!!)?.id) {
            throw DontExistException("Room (id=${s.id}) doesn't exist in database.")
        }
        ConsigneRules.check(c.value!!)
    }

}
