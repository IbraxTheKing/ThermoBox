package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.images.ImageProcessor
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomImage
import ax.ibr.utils.exceptions.AlreadyExistsException
import ax.ibr.thermobox.restserver.dto.version
import ax.ibr.thermobox.restserver.security.scope
import ax.ibr.utils.rest.RequiresAuth
import ax.ibr.utils.rest.RequiresRole
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import jakarta.ws.rs.core.Response

@Path("api/v2/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class RoomResource {

    private val service = BusinessFactory.getRoomService()
    private val images = BusinessFactory.getRoomImageService()

    @Context
    private lateinit var security: SecurityContext

    /**
     * Only the buildings, floors, rooms and devices the user may see. An element that exists but
     * isn't allowed answers exactly like an unknown one: its existence isn't disclosed.
     */
    @GET
    fun getAll(): List<Room> {
        val access = security.scope
        return service.getAll().filter { access.canSee(it) }
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: Long): Room? {
        return service.getById(id)?.takeIf { security.scope.canSee(it) }
    }

    @GET
    @Path("/name/{name}")
    fun getByName(@PathParam("name") name: String): Room? {
        return service.getByName(name)?.takeIf { security.scope.canSee(it) }
    }

    @POST
    @RequiresRole("ADMIN")
    fun add(room: Room): Response {
        return try {
            service.add(room)
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
    fun updateById(@PathParam("id") id: Long, room: Room) {
        room.id = id
        service.update(room)
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole("ADMIN")
    fun removeById(@PathParam("id") id: Long) {
        val room = service.getById(id)
        if (room != null) {
            service.remove(room)
        }
    }

    /**
     * Photo of an element (JPEG), for the users who may see it; 404 without photo. Clients add
     * the version given by GET /api/v2/hierarchy/tree (?v=…) to the URL, so a new photo comes
     * with a new URL; the ETag lets them check a cached copy cheaply.
     */
    @GET
    @Path("/{id}/image")
    @Produces(ImageProcessor.CONTENT_TYPE)
    fun getImage(@PathParam("id") id: Long, @HeaderParam("If-None-Match") clientEtag: String?): Response {
        val room = service.getById(id)?.takeIf { security.scope.canSee(it) } ?: return Response.status(Response.Status.NOT_FOUND).build()
        val image = images.getByRoom(room) ?: return Response.status(Response.Status.NOT_FOUND).build()
        val etag = "\"${version(image.updated)}\""
        // Not "immutable": a withdrawn access must not leave the photo in a cache for a year.
        val response = if (clientEtag == etag) Response.notModified() else Response.ok(image.data, image.contentType)
        return response
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
        val room = service.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (data == null || data.isEmpty()) return imageRejected("The file is empty.")
        if (data.size > ImageProcessor.MAX_UPLOAD_BYTES) return imageRejected("The image must not exceed ${ImageProcessor.MAX_UPLOAD_BYTES / 1024 / 1024} Mo.")
        return try {
            images.add(RoomImage(room, ImageProcessor.CONTENT_TYPE, data))
            val image = images.getByRoom(room)
            Response.ok(mapOf("image" to image?.let { version(it.updated) })).build()
        } catch (e: IllegalArgumentException) {
            imageRejected(e.message ?: "Image refused")
        }
    }

    @DELETE
    @Path("/{id}/image")
    @RequiresRole("ADMIN")
    fun deleteImage(@PathParam("id") id: Long): Response {
        val room = service.getById(id) ?: return Response.status(Response.Status.NOT_FOUND).build()
        images.getByRoom(room)?.let { images.remove(it) }
        return Response.noContent().build()
    }

    private fun imageRejected(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE).entity(mapOf("error" to message)).build()
}