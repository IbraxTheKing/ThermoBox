package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response

class TemperatureResource {

    private val service = BusinessFactory().getTemperatureService()

    @GET
    @Path("/temp")
    fun getAll() : List<Temperature> {
        return service.getAll()
    }





}