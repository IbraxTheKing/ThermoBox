package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Salle
import jakarta.ws.rs.GET

class SalleResource {

    private val service = BusinessFactory().getSalleService()

    @GET
    fun getAll() : List<Salle> {
        return service.getAll()
    }

}