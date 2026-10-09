package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

// The data service is a constructor parameter so tests can pass a mock.
class SalleServiceImpl(
    private val salleService: SalleService = PersistenceFactory.getSalleDataService()
) : SalleService, Serializable {

    override fun getByAllType(t: Salle) : List<Salle> {
        return salleService.getByAllType(t)
    }

    override fun getByName(name: String): Salle? {
        return salleService.getByName(name)
    }

    override fun add(t: Salle) {
        salleService.add(t)
    }

    override fun update(t: Salle) {
        salleService.update(t)
    }

    override fun remove(t: Salle) {
        salleService.remove(t)
    }

    override fun getAll(): List<Salle> {
        return salleService.getAll()
    }

    override fun getById(id: Long): Salle? {
        return salleService.getById(id)
    }
}