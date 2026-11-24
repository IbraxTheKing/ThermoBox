package ax.ibr.thermobox.restserver.resources

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.implementations.ClimatServiceImpl
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.ConsigneRequest
import ax.ibr.thermobox.restserver.dto.EnvoiConsigneDto
import ax.ibr.thermobox.restserver.dto.HistoriqueDto
import ax.ibr.thermobox.restserver.dto.PointDto
import ax.ibr.thermobox.restserver.dto.SerieDto
import ax.ibr.thermobox.restserver.hierarchie.VueUtilisateur
import ax.ibr.thermobox.restserver.security.currentUser
import ax.ibr.thermobox.restserver.security.perimetre
import ax.ibr.utils.rest.RequiresAuth
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Temperatures at the level of any element of the hierarchy (ClimatService): a salle, an étage or
 * a bâtiment aggregates the boxes it contains, and follows the consigne of its nearest ancestor
 * when it has none of its own. Only what the current user may see.
 *
 * Periods are given as ISO-8601 instants: ?start=2026-10-20T08:00:00Z&end=2026-10-21T08:00:00Z.
 */
@Path("api/climat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ClimatResource {

    private val climat = BusinessFactory.getClimatService()
    private val hierarchie = BusinessFactory.getHierarchieService()

    @Context
    private lateinit var security: SecurityContext

    private val vue by lazy { VueUtilisateur(security.currentUser, security.perimetre) }

    /**
     * Curves of an element over the period: one per box for a salle (GESTIONNAIRE, ADMIN), one
     * per salle for an étage or a bâtiment, their average, and the consignes.
     */
    @GET
    @Path("/{id}/historique")
    fun historique(@PathParam("id") id: Long, @QueryParam("start") start: String?, @QueryParam("end") end: String?): Response {
        val s = vue.element(id) ?: return introuvable()
        val (de, a) = periode(start, end) ?: return erreur("start and end must be ISO-8601 instants, start before end")

        val historiques = climat.getHistoriques(s, de, a)
        val consignes = climat.getConsignes(s, de, a).map { PointDto.of(it) }
        val moyenne = ClimatServiceImpl.moyenneGlissante(historiques).map { PointDto.of(it) }
        val conteneur = s.kind == SalleKind.ETAGE || s.kind == SalleKind.BATIMENT

        val dto = when {
            conteneur -> HistoriqueDto(
                boitiers = emptyList(),
                // One curve per salle (average of its boxes) rather than one per box.
                salles = hierarchie.getSalles(s).map { salle ->
                    val ids = hierarchie.getBoitiers(salle).mapNotNull { it.id }.toSet()
                    SerieDto(salle.id, salle.name, ClimatServiceImpl.moyenneGlissante(historiques.filter { it.boitier.id in ids }).map { PointDto.of(it) })
                }.filter { it.points.isNotEmpty() },
                moyenne = moyenne,
                consignes = consignes,
            )
            // VIEWER: the temperature of the salle, not one curve per box.
            !vue.boitiersVisibles -> HistoriqueDto(emptyList(), emptyList(), moyenne, consignes)
            else -> HistoriqueDto(
                boitiers = historiques.map { h -> SerieDto(h.boitier.id, h.boitier.name, h.mesures.map { PointDto.of(it) }) },
                salles = emptyList(),
                moyenne = moyenne,
                consignes = consignes,
            )
        }
        return Response.ok(dto).build()
    }

    /**
     * Average curve of several elements at once (dashboard sparklines, Mesures page):
     * ?ids=3,7,12&start=…&end=… gives {"3": [points…], "7": […]}. Elements the user may not see
     * are left out.
     */
    @GET
    @Path("/moyennes")
    fun moyennes(@QueryParam("ids") ids: String?, @QueryParam("start") start: String?, @QueryParam("end") end: String?): Response {
        val (de, a) = periode(start, end) ?: return erreur("start and end must be ISO-8601 instants, start before end")
        val demandes = ids.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
        if (demandes.size > MAX_ELEMENTS) return erreur("At most $MAX_ELEMENTS ids per request")
        val resultat = LinkedHashMap<String, List<PointDto>>()
        for (id in demandes) {
            val s = vue.element(id) ?: continue
            resultat[id.toString()] = climat.getMoyenneHistorique(s, de, a).map { PointDto.of(it) }
        }
        return Response.ok(resultat).build()
    }

    /**
     * {"value": 21.5, "force": false}: saves the consigne (Celsius) of a bâtiment, an étage or a
     * salle and sends it to the boxes below. Those below with their own consigne keep it unless
     * "force" is true.
     */
    @PUT
    @Path("/{id}/consigne")
    @RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"], allowOwner = false)
    fun envoyerConsigne(@PathParam("id") id: Long, demande: ConsigneRequest?): Response {
        val s = vue.element(id) ?: return introuvable()
        if (s is Boitier) return erreur("Une consigne se règle sur un bâtiment, un étage ou une salle")
        if (demande == null) return erreur("Missing body")
        val valeur = demande.value ?: return erreur("value is required")
        return try {
            Response.ok(EnvoiConsigneDto.of(climat.envoyerConsigne(s, valeur, demande.force))).build()
        } catch (e: Exception) {
            // Refused value (too hot, impossible): nothing was saved.
            erreur(e.message ?: "Consigne refusée")
        }
    }

    private fun periode(start: String?, end: String?): Pair<Instant, Instant>? = try {
        val de = Instant.parse(start ?: return null)
        val a = Instant.parse(end ?: return null)
        if (de.isBefore(a)) de to a else null
    } catch (e: DateTimeParseException) {
        null
    }

    private fun introuvable(): Response = Response.status(Response.Status.NOT_FOUND).build()

    private fun erreur(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

    private companion object {
        const val MAX_ELEMENTS = 200
    }
}
