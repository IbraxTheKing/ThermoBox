package ax.ibr.thermobox.restserver.dto

/**
 * A room that started or stopped respecting its setpoint (GET /api/v2/notifications/setpoints):
 * [state] and [previous] are STABLE (respected), HEATING (below) or TOO_HOT (above); [at] when the
 * new state started (local time of the server, as every date of the API); [temperature] its
 * average then, [setpoint] the one it is held to; [place] « Bâtiment A › Étage 1 ».
 */
data class SetpointEventDto(
    val roomId: Long?,
    val name: String?,
    val place: String,
    val state: String,
    val previous: String,
    val temperature: Float,
    val setpoint: Float,
    val at: String?,
)

/** The changes between [since] and [now]: the next request asks since [now]. */
data class SetpointEventsDto(
    val since: String?,
    val now: String?,
    val events: List<SetpointEventDto>,
)
