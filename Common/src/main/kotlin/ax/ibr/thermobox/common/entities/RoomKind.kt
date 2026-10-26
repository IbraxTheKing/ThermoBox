package ax.ibr.thermobox.common.entities

/** Level of a [Room] in the building hierarchy, from the top: Building → (Floor) → Room → Device. */
enum class RoomKind(val label: String, val withArticle: String) {
    BUILDING("Building", "a building"),
    FLOOR("Floor", "a floor"),
    ROOM("Room", "a room"),
    DEVICE("Device", "a device");

    /** Kinds that may contain this one: a room can sit on a floor or directly in a building. */
    val parentKinds: List<RoomKind>
        get() = when (this) {
            BUILDING -> emptyList()
            FLOOR -> listOf(BUILDING)
            ROOM -> listOf(FLOOR, BUILDING)
            DEVICE -> listOf(ROOM)
        }

    /** Kinds this one may contain. */
    val childKinds: List<RoomKind> get() = entries.filter { this in it.parentKinds }

    fun create(name: String?): Room = when (this) {
        BUILDING -> Building(name)
        FLOOR -> Floor(name)
        ROOM -> Room(name)
        DEVICE -> Device(name)
    }
}

val Room.kind: RoomKind
    get() = when (this) {
        is Building -> RoomKind.BUILDING
        is Floor -> RoomKind.FLOOR
        is Device -> RoomKind.DEVICE
        else -> RoomKind.ROOM
    }
