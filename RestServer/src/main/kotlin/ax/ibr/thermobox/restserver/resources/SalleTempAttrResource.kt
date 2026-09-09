package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response

class SalleTempAttrResource {

    private val service = BusinessFactory().getSalleTempAttrService()
    private lateinit var requestBoxes: ProtocolDriver

    @GET
    @Path("/salletemps/")
    fun getAll(): List<SalleTempAttr> {
        return service.getAll()
    }

    @GET
    @Path("/room/{id}/")
    fun getBySalleId(@PathParam("id") id: Long): SalleTempAttr? {
        return service.getBySalle(id)
    }

    @PUT
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], false)
    @Path("room/{id}/temperature/consigne/{consigne}")
    fun sendConsigne(@PathParam("consigne") consigne: Consigne, @PathParam("id") id: Long): Response {
        try {
            requestBoxes.sendConsigne(service.getBySalle(id), consigne)
        } catch (ex: Exception) {
            throw ex
        }

        return Response.ok().build()
    }


}