package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.images.ImageProcessor
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleImage
import ax.ibr.thermobox.restserver.dto.version
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("api/salles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SalleResource {

    private val service = BusinessFactory.getSalleService()
    private val images = BusinessFactory.getSalleImageService()

    @GET
    fun getAll(): List<Salle> {
        return service.getAll()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): Salle? {
        return service.getById(id)
    }

    @GET
    @Path("/name/{name}")
    fun getByName(@PathParam("name") name: String): Salle? {
        return service.getByName(name)
    }

    @POST
    @RequiresRole("ADMIN")
    fun add(salle: Salle): Response {
        return try {
            service.add(salle)
            Response
                .status(Response.Status.CREATED)
                .build()
        } catch (e: AlreadyExistsException) {
            Response
                .status(Response.Status.CONFLICT)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }

    @PUT
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun updateById(@PathParam("id") id: Long, salle: Salle) {
        salle.id = id
        service.update(salle)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun removeById(@PathParam("id") id: Long) {
        val salle = service.getById(id)
        if (salle != null) {
            service.remove(salle)
        }
    }

    /**
     * Photo of an element (JPEG); 404 without photo. Clients add the version given by
     * GET /api/hierarchie/arbre (?v=…) to the URL, so a new photo comes with a new URL; the ETag
     * lets them check a cached copy cheaply.
     */
    @GET
    @Path("/{id}/image")
    @Produces(ImageProcessor.CONTENT_TYPE)
    fun getImage(@PathParam("id") id: Long, @HeaderParam("If-None-Match") etagClient: String?): Response {
        val salle = service.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        val image = images.getBySalle(salle) ?: return Response.status(Response.Status.NOT_FOUND).build()
        val etag = "\"${version(image.updated)}\""
        val reponse = if (etagClient == etag) Response.notModified() else Response.ok(image.data, image.contentType)
        return reponse
            .header("ETag", etag)
            .header("Cache-Control", "private, no-cache")
            .header("X-Content-Type-Options", "nosniff")
            .build()
    }

    /**
     * Replaces the photo (ADMIN): the body is the image itself (JPEG, PNG, GIF or BMP, 10 MB at
     * most), re-encoded as a reduced JPEG without its metadata.
     */
    @PUT
    @Path("/{id}/image")
    @Consumes(MediaType.WILDCARD)
    @RequiresRole("ADMIN")
    fun putImage(@PathParam("id") id: Long, data: ByteArray?): Response {
        val salle = service.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (data == null || data.isEmpty()) return imageRefusee("Le fichier est vide.")
        if (data.size > ImageProcessor.MAX_UPLOAD_BYTES) return imageRefusee("L'image ne doit pas dépasser ${ImageProcessor.MAX_UPLOAD_BYTES / 1024 / 1024} Mo.")
        return try {
            images.add(SalleImage(salle, ImageProcessor.CONTENT_TYPE, data))
            val image = images.getBySalle(salle)
            Response.ok(mapOf("image" to image?.let { version(it.updated) })).build()
        } catch (e: IllegalArgumentException) {
            imageRefusee(e.message ?: "Image refusée")
        }
    }

    @DELETE
    @Path("/{id}/image")
    @RequiresRole("ADMIN")
    fun deleteImage(@PathParam("id") id: Long): Response {
        val salle = service.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        images.getBySalle(salle)?.let { images.remove(it) }
        return Response.noContent().build()
    }

    private fun imageRefusee(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE).entity(mapOf("error" to message)).build()
}
