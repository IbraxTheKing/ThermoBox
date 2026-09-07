package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.utils.services.CrudService

interface SalleService : CrudService<Salle> {

    fun getByName(name: String): Salle?
}