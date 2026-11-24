package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.ConsigneEffective
import ax.ibr.thermobox.common.services.EnvoiConsigne
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * JSON of the bâtiment → étage → salle → boîtier tree (/api/hierarchie, /api/climat, /api/favoris).
 * Unlike /api/salles, every element carries its kind (BATIMENT, ETAGE, SALLE, BOITIER), and dates
 * are ISO-8601 strings in the server's time zone ("2026-10-21T14:05:00"), like /api/updates.
 */

/** An element seen from above: enough to place it (breadcrumb, tree, parent lists). */
data class ElementDto(val id: Long?, val name: String?, val kind: String, val accessible: Boolean) {
    companion object {
        fun of(s: Salle, accessible: Boolean = true) = ElementDto(s.id, s.name, s.kind.name, accessible)
    }
}

/** One measure or consigne. */
data class PointDto(val date: String?, val value: Float?) {
    companion object {
        fun of(t: Temperature) = PointDto(iso(t.date), t.value)
    }
}

/**
 * The consigne that applies to an element: its own, or that of its nearest ancestor ([heritee],
 * [sourceId] / [sourceName] then name that ancestor).
 */
data class ConsigneDto(val value: Float?, val date: String?, val heritee: Boolean, val sourceId: Long?, val sourceName: String?) {
    companion object {
        fun of(c: ConsigneEffective) = ConsigneDto(c.consigne.value, iso(c.consigne.date), c.heritee, c.source.id, c.source.name)
        fun of(c: Consigne) = ConsigneDto(c.value, iso(c.date), false, null, null)
    }
}

/**
 * One node of GET /api/hierarchie/arbre.
 *
 * - [parentId]: null for a bâtiment, or for an element not attached yet ([rattache] false).
 * - [accessible] false: an ancestor shown only to place what the user may see below it. Its
 *   name only: no temperature, consigne, photo.
 * - [temperature]: last measure of a box, or average of the last measures of the boxes below.
 * - [boitiers]: number of boxes below (0 for a VIEWER, who doesn't see the hardware).
 * - [image]: version of the photo (GET /api/salles/{id}/image?v=…), null without photo.
 */
data class NoeudDto(
    val id: Long?,
    val name: String?,
    val kind: String,
    val parentId: Long?,
    val rattache: Boolean,
    val accessible: Boolean,
    val temperature: Float?,
    val derniereMesure: String?,
    val boitiers: Int,
    val consigne: ConsigneDto?,
    val image: Long?,
    val favori: Boolean,
)

/**
 * GET /api/hierarchie/arbre. [tout]: the user sees everything (no limited accesses), so the
 * average of a bâtiment is that of all its boxes; otherwise clients only use what they may see.
 */
data class ArbreDto(val tout: Boolean, val boitiersVisibles: Boolean, val noeuds: List<NoeudDto>)

/** Latest measure of one box of a salle. */
data class MesureBoitierDto(val id: Long?, val name: String?, val value: Float?, val date: String?)

/** GET /api/hierarchie/elements/{id}: an element with everything its page shows. */
data class DetailDto(
    val element: NoeudDto,
    /** From the bâtiment down to the direct parent. */
    val ancetres: List<ElementDto>,
    /** Direct children (no boxes for a VIEWER). */
    val enfants: List<ElementDto>,
    /** Latest measure of every box below (empty for a VIEWER). */
    val mesures: List<MesureBoitierDto>,
    /** Étages and salles below that keep their own consigne when one is sent here. */
    val consignesPropres: List<ConsigneDto>,
    /** Étage or bâtiment: every salle below, at any depth. */
    val salles: List<NoeudDto>,
)

/** One curve of GET /api/climat/{id}/historique: a box, or a salle of an étage / bâtiment. */
data class SerieDto(val id: Long?, val name: String?, val points: List<PointDto>)

data class HistoriqueDto(
    /** One curve per box (salle seen by a GESTIONNAIRE or an ADMIN). */
    val boitiers: List<SerieDto>,
    /** One curve per salle (étage or bâtiment). */
    val salles: List<SerieDto>,
    /** Average of the boxes over time. */
    val moyenne: List<PointDto>,
    /** Consignes of the period, oldest first. */
    val consignes: List<PointDto>,
)

/** PUT /api/climat/{id}/consigne: {"value": 21.5, "force": false} (Celsius). */
class ConsigneRequest {
    var value: Float? = null
    var force: Boolean = false
}

/** Outcome of a consigne sent to an element and what it contains. */
data class EnvoiConsigneDto(
    /** Étages / salles below that kept their own consigne. */
    val conservees: List<ConsigneDto>,
    /** Étages / salles below whose own consigne was replaced (force). */
    val remplacees: List<ElementDto>,
    /** Boxes the consigne was sent to. */
    val boitiers: List<ElementDto>,
    /** Boxes it could not be sent to. */
    val echecs: List<ElementDto>,
) {
    companion object {
        fun of(e: EnvoiConsigne) = EnvoiConsigneDto(
            e.conservees.map { ConsigneDto.of(it) },
            e.remplacees.map { ElementDto.of(it) },
            e.boitiers.map { ElementDto.of(it) },
            e.echecs.map { ElementDto.of(it) },
        )
    }
}

/** POST /api/hierarchie/elements: {"kind": "SALLE", "name": "…", "parentId": 3}; PUT: name and parentId. */
class ElementRequest {
    var kind: String? = null
    var name: String? = null
    var parentId: Long? = null
}

/** PUT /api/hierarchie/elements/{id}/parent: {"parentId": 3}, or null to detach. */
class ParentRequest {
    var parentId: Long? = null
}

/** GET / PUT /api/reglages: look of every client, chosen by an ADMIN ("verre" or "panneau"). */
data class ReglagesDto(val style: String)

class ReglagesRequest {
    var style: String? = null
}

internal fun iso(date: LocalDateTime?): String? = date?.toString()

/** Version of a photo, as in its URL: changes whenever the photo does. */
internal fun version(date: LocalDateTime): Long = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
