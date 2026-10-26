package ax.ibr.thermobox.restserver.hierarchy

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.access.AccessScope
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.TreeDto
import ax.ibr.thermobox.restserver.dto.SetpointDto
import ax.ibr.thermobox.restserver.dto.DetailDto
import ax.ibr.thermobox.restserver.dto.ElementDto
import ax.ibr.thermobox.restserver.dto.DeviceMeasurementDto
import ax.ibr.thermobox.restserver.dto.NodeDto
import ax.ibr.thermobox.restserver.dto.iso
import ax.ibr.thermobox.restserver.dto.version

/**
 * The buildings, floors, rooms and devices as one user sees them, for one request: what they may
 * see (AccessService), their favorites, and the devices only for a MANAGER or an ADMIN (a
 * VIEWER sees buildings, floors, rooms and their temperatures, not the hardware).
 *
 * An element outside the user's accesses is never described: [element] answers null for it,
 * exactly as for an unknown id.
 */
class UserView(private val user: User, private val access: AccessScope) {

    private val rooms = BusinessFactory.getRoomService()
    private val hierarchy = BusinessFactory.getHierarchyService()
    private val climate = BusinessFactory.getClimateService()

    /** Boxes are hardware: MANAGER and ADMIN only. */
    val devicesVisible: Boolean = user.type?.hasRole("MANAGER") == true

    private val favorites: Set<Long> by lazy {
        BusinessFactory.getFavoriteService().getByUser(user).mapNotNull { it.room.id }.toSet()
    }

    private val images by lazy { BusinessFactory.getRoomImageService().getDates() }

    // Every link at once: one query rather than one per element.
    private val parents: Map<Long, Long> by lazy {
        BusinessFactory.getRoomRoomAttrService().getAll()
            .mapNotNull { l -> l.roomB.id?.let { child -> l.roomA.id?.let { parent -> child to parent } } }
            .toMap()
    }

    /** Shown at all: allowed, or an ancestor of something allowed (name only). */
    fun visible(s: Room): Boolean = access.isVisible(s) && (s !is Device || devicesVisible)

    /** Its data may be read: allowed (and not a box for a VIEWER). */
    fun accessible(s: Room): Boolean = access.canSee(s) && (s !is Device || devicesVisible)

    /** The element [id] if this user may open it, null otherwise (same answer as for an unknown id). */
    fun element(id: Long): Room? = rooms.getById(id)?.takeIf { accessible(it) }

    fun tree(): TreeDto {
        val nodes = rooms.getAll().filter { it.id != null && visible(it) }
            .sortedWith(compareBy({ it.kind }, { it.name?.lowercase() }))
            .map { node(it) }
        return TreeDto(access.all, devicesVisible, nodes)
    }

    fun node(s: Room): NodeDto {
        val parentId = parents[s.id]
        val attached = parentId != null || s.kind == RoomKind.BUILDING
        if (!accessible(s)) {
            return NodeDto(s.id, s.name, s.kind.name, parentId, attached, false, null, null, 0, null, null, false)
        }
        val measurements = climate.getMeasurements(s)
        val valeurs = measurements.mapNotNull { it.measurement?.value }
        val temperature = if (s is Device) valeurs.firstOrNull() else valeurs.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return NodeDto(
            id = s.id,
            name = s.name,
            kind = s.kind.name,
            parentId = parentId,
            attached = attached,
            accessible = true,
            temperature = temperature,
            lastMeasurement = iso(measurements.mapNotNull { it.measurement?.date }.maxOrNull()),
            devices = if (devicesVisible) measurements.size else 0,
            setpoint = if (s is Device) null else climate.getEffectiveSetpoint(s)?.let { SetpointDto.of(it) },
            image = images[s.id]?.let { version(it) },
            favorite = s.id in favorites,
        )
    }

    fun detail(s: Room): DetailDto {
        val device = s is Device
        val container = s.kind == RoomKind.FLOOR || s.kind == RoomKind.BUILDING
        return DetailDto(
            element = node(s),
            ancestors = hierarchy.getPath(s).map { ElementDto.of(it, accessible(it)) },
            children = if (device) emptyList() else hierarchy.getChildren(s).filter { visible(it) }.map { ElementDto.of(it, accessible(it)) },
            measurements = if (!devicesVisible) emptyList() else climate.getMeasurements(s).map {
                DeviceMeasurementDto(it.device.id, it.device.name, it.measurement?.value, iso(it.measurement?.date))
            },
            ownSetpoints = if (device) emptyList() else climate.getOwnSetpoints(s).map { SetpointDto.of(it) },
            rooms = if (container) hierarchy.getRooms(s).filter { accessible(it) }.map { node(it) } else emptyList(),
        )
    }
}
