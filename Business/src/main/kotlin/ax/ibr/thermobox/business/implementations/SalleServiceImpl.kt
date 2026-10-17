package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.services.FavoriService
import ax.ibr.thermobox.common.services.SalleImageService
import ax.ibr.thermobox.common.services.SalleSalleAttrService
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

// The data service is a constructor parameter so tests can pass a mock.
class SalleServiceImpl(
    private val salleService: SalleService = PersistenceFactory.getSalleDataService()
) : SalleService, Serializable {

    // Only needed by remove(); lazy so tests of the other methods don't open the database.
    private val links: SalleSalleAttrService by lazy { PersistenceFactory.getSalleSalleAttrDataService() }
    private val history: SalleTempAttrService by lazy { PersistenceFactory.getSalleTempAttrDataService() }
    private val favoris: FavoriService by lazy { PersistenceFactory.getFavoriDataService() }
    private val images: SalleImageService by lazy { PersistenceFactory.getSalleImageDataService() }

    override fun getAllByType(c: Salle) : List<Salle> {
        return salleService.getAllByType(c)
    }

    override fun getByName(name: String): Salle? {
        return salleService.getByName(name)
    }

    @Transactional
    override fun add(t: Salle) {
        salleService.add(t)
    }

    @Transactional
    override fun update(t: Salle) {
        salleService.update(t)
    }

    @Transactional
    /**
     * Deletes the element with its history, its image and the favourites pointing to it. Its links go too:
     * its children are kept but become unattached. Without this the foreign keys would block the delete.
     */
    override fun remove(t: Salle) {
        if (t.id != null) {
            favoris.getBySalle(t).forEach { favoris.remove(it) }
            images.getBySalle(t)?.let { images.remove(it) }
        }
        links.getBySalle(t).forEach { links.remove(it) }
        history.getBySalle(t).orEmpty().forEach { history.remove(it) }
        salleService.remove(t)
    }

    override fun getAll(): List<Salle> {
        return salleService.getAll()
    }

    override fun getById(id: Long): Salle? {
        return salleService.getById(id)
    }
}