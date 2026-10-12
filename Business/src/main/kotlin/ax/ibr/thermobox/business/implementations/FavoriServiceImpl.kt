package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.FavoriService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

class FavoriServiceImpl(
    private val favoriService: FavoriService = PersistenceFactory.getFavoriDataService()
) : FavoriService, Serializable {

    override fun getByUser(user: User): List<Favori> = favoriService.getByUser(user)

    override fun getBySalle(salle: Salle): List<Favori> = favoriService.getBySalle(salle)

    /** Does nothing if the salle is already a favourite of this user. */
    override fun add(t: Favori) {
        if (getByUser(t.user).none { it.salle.id == t.salle.id }) favoriService.add(t)
    }

    override fun update(t: Favori) = favoriService.update(t)

    override fun remove(t: Favori) = favoriService.remove(t)

    override fun getAll(): List<Favori> = favoriService.getAll()

    override fun getById(id: Long): Favori? = favoriService.getById(id)
}
