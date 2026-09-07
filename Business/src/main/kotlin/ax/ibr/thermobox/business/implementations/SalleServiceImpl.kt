package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class SalleServiceImpl : SalleService {

    private val salleService: SalleService = PersistenceFactory().getSalleDataService()

    override fun getByName(name: String): Salle? {
        TODO("Not yet implemented")
    }

    override fun add(t: Salle) {
        TODO("Not yet implemented")
    }

    override fun update(t: Salle) {
        TODO("Not yet implemented")
    }

    override fun remove(t: Salle) {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<Salle> {
        TODO("Not yet implemented")
    }

    override fun getById(id: Long): Salle? {
        TODO("Not yet implemented")
    }
}