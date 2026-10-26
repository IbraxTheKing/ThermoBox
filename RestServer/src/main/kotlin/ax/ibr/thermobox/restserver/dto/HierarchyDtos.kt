package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.EffectiveSetpoint
import ax.ibr.thermobox.common.services.SetpointDispatch
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * JSON of the building → floor → room → device tree (/api/v2/hierarchy, /api/v2/climate,
 * /api/v2/favorites). Unlike /api/v2/rooms, every element carries its kind (BUILDING, FLOOR, ROOM,
 * DEVICE), and dates are ISO-8601 strings in the server's time zone ("2026-10-21T14:05:00"), like
 * /api/v2/updates.
 */

/** An element seen from above: enough to place it (breadcrumb, tree, parent lists). */
data class ElementDto(val id: Long?, val name: String?, val kind: String, val accessible: Boolean) {
    companion object {
        fun of(s: Room, accessible: Boolean = true) = ElementDto(s.id, s.name, s.kind.name, accessible)
    }
}

/** One measurement or setpoint. */
data class PointDto(val date: String?, val value: Float?) {
    companion object {
        fun of(t: Temperature) = PointDto(iso(t.date), t.value)
    }
}

/**
 * The setpoint that applies to an element: its own, or that of its nearest ancestor ([inherited],
 * [sourceId] / [sourceName] then name that ancestor).
 */
data class SetpointDto(val value: Float?, val date: String?, val inherited: Boolean, val sourceId: Long?, val sourceName: String?) {
    companion object {
        fun of(c: EffectiveSetpoint) = SetpointDto(c.setpoint.value, iso(c.setpoint.date), c.inherited, c.source.id, c.source.name)
        fun of(c: Setpoint) = SetpointDto(c.value, iso(c.date), false, null, null)
    }
}

/**
 * One node of GET /api/v2/hierarchy/tree.
 *
 * - [parentId]: null for a building, or for an element not attached yet ([attached] false).
 * - [accessible] false: an ancestor shown only to place what the user may see below it. Its
 *   name only: no temperature, setpoint, photo.
 * - [temperature]: last measurement of a device, or average of the last measurements of the devices below.
 * - [devices]: number of devices below (0 for a VIEWER, who doesn't see the hardware).
 * - [image]: version of the photo (GET /api/v2/rooms/{id}/image?v=…), null without photo.
 */
data class NodeDto(
    val id: Long?,
    val name: String?,
    val kind: String,
    val parentId: Long?,
    val attached: Boolean,
    val accessible: Boolean,
    val temperature: Float?,
    val lastMeasurement: String?,
    val devices: Int,
    val setpoint: SetpointDto?,
    val image: Long?,
    val favorite: Boolean,
)

/**
 * GET /api/v2/hierarchy/tree. [all]: the user sees everything (no limited accesses), so the
 * average of a building is that of all its devices; otherwise clients only use what they may see.
 */
data class TreeDto(val all: Boolean, val devicesVisible: Boolean, val nodes: List<NodeDto>)

/** Latest measurement of one device of a room. */
data class DeviceMeasurementDto(val id: Long?, val name: String?, val value: Float?, val date: String?)

/** GET /api/v2/hierarchy/elements/{id}: an element with everything its page shows. */
data class DetailDto(
    val element: NodeDto,
    /** From the building down to the direct parent. */
    val ancestors: List<ElementDto>,
    /** Direct children (no boxes for a VIEWER). */
    val children: List<ElementDto>,
    /** Latest measure of every box below (empty for a VIEWER). */
    val measurements: List<DeviceMeasurementDto>,
    /** Floors and rooms below that keep their own setpoint when one is sent here. */
    val ownSetpoints: List<SetpointDto>,
    /** Floor or building: every room below, at any depth. */
    val rooms: List<NodeDto>,
)

/** One curve of GET /api/v2/climate/{id}/history: a device, or a room of a floor / building. */
data class SeriesDto(val id: Long?, val name: String?, val points: List<PointDto>)

data class HistoryDto(
    /** One curve per device (room seen by a MANAGER or an ADMIN). */
    val devices: List<SeriesDto>,
    /** One curve per room (floor or building). */
    val rooms: List<SeriesDto>,
    /** Average of the boxes over time. */
    val average: List<PointDto>,
    /** Setpoints of the period, oldest first. */
    val setpoints: List<PointDto>,
)

/** PUT /api/v2/climate/{id}/setpoint: {"value": 21.5, "force": false} (Celsius). */
class SetpointRequest {
    var value: Float? = null
    var force: Boolean = false
}

/** Outcome of a setpoint sent to an element and what it contains. */
data class SetpointDispatchDto(
    /** Floors / rooms below that kept their own setpoint. */
    val kept: List<SetpointDto>,
    /** Floors / rooms below whose own setpoint was replaced (force). */
    val replaced: List<ElementDto>,
    /** Devices the setpoint was sent to. */
    val devices: List<ElementDto>,
    /** Boxes it could not be sent to. */
    val failures: List<ElementDto>,
) {
    companion object {
        fun of(e: SetpointDispatch) = SetpointDispatchDto(
            e.kept.map { SetpointDto.of(it) },
            e.replaced.map { ElementDto.of(it) },
            e.devices.map { ElementDto.of(it) },
            e.failures.map { ElementDto.of(it) },
        )
    }
}

/** POST /api/v2/hierarchy/elements: {"kind": "ROOM", "name": "…", "parentId": 3}; PUT: name and parentId. */
class ElementRequest {
    var kind: String? = null
    var name: String? = null
    var parentId: Long? = null
}

/** PUT /api/v2/hierarchy/elements/{id}/parent: {"parentId": 3}, or null to detach. */
class ParentRequest {
    var parentId: Long? = null
}

/** GET / PUT /api/v2/settings: look of every client, chosen by an ADMIN ("glass" or "panel"). */
data class SettingsDto(val style: String)

class SettingsRequest {
    var style: String? = null
}

internal fun iso(date: LocalDateTime?): String? = date?.toString()

/** Version of a photo, as in its URL: changes whenever the photo does. */
internal fun version(date: LocalDateTime): Long = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
