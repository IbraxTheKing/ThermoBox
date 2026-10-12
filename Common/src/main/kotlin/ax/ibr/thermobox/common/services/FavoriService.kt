package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Favori
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

interface FavoriService : CrudService<Favori> {

    fun getByUser(user: User): List<Favori>

    fun getBySalle(salle: Salle): List<Favori>
}
