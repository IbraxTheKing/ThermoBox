package ax.ibr.thermobox.common.alerts

import java.time.LocalDateTime

/*
 * Temperature alerts (26.12): a room whose measures stay above its setpoint is reported to people
 * outside ThermoBox, by email or webhook. Kept in the alert_* tables (migration V9); dates are
 * local date-times of the server, as everywhere in the database.
 */

/** How a channel reaches people. */
enum class ChannelKind { EMAIL, WEBHOOK }

/**
 * A way to reach people: an SMTP server and its recipients, or a webhook URL.
 *
 * [settings] is the JSON of what may be shown (host, port, recipients, format…); [secret] the
 * encrypted JSON of what must never leave the server (SMTP password, webhook URL and token):
 * the business layer reads and writes both, the database only keeps them.
 */
data class AlertChannel(
    val id: Long? = null,
    val name: String,
    val kind: ChannelKind,
    val enabled: Boolean = true,
    val settings: String = "{}",
    val secret: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

/**
 * When to alert, and whom.
 *
 * A room is in excess when a box of it measures more than its setpoint + [tolerance] (Celsius)
 * for at least [minDurationSeconds]; the alert is resolved once every box has been back under
 * for [clearAfterSeconds]. While it lasts, it is sent again every [reminderSeconds] (0: never).
 *
 * Scope: the whole installation when [global], otherwise the buildings, floors and rooms of
 * [scope] (with what they contain). Where several rules cover a room, the nearest one applies
 * (a rule on the room before one on its floor, its building, then the global ones).
 */
data class AlertRule(
    val id: Long? = null,
    val name: String,
    val enabled: Boolean = true,
    val tolerance: Float = 1f,
    val minDurationSeconds: Int = 600,
    val clearAfterSeconds: Int = 600,
    val reminderSeconds: Int = 0,
    val notifyResolution: Boolean = true,
    val global: Boolean = true,
    /** Building, floor or room ids (ignored when [global]). */
    val scope: List<Long> = emptyList(),
    val channels: List<Long> = emptyList(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

enum class AlertState {
    /** The room is (still) in excess. */
    ACTIVE,
    /** Back under the threshold. */
    RESOLVED,
    /** Closed without a return to normal: rule disabled or deleted, room deleted, no more setpoint. */
    CANCELLED,
}

/** A box of the room, at the last evaluation. */
data class AlertDevice(
    val id: Long,
    val name: String?,
    val temperature: Float?,
    val at: LocalDateTime?,
    /** Above the threshold. */
    val exceeding: Boolean,
)

/**
 * One excess of one room under one rule, from its detection to its end. The names (rule, room,
 * place) are copied: the history stays readable after they are renamed or deleted.
 */
data class AlertEvent(
    val id: Long? = null,
    val ruleId: Long?,
    val ruleName: String,
    val roomId: Long,
    val roomName: String?,
    /** « Bâtiment A › Étage 1 ». */
    val place: String,
    val state: AlertState = AlertState.ACTIVE,
    /** First measure above the threshold. */
    val exceededSince: LocalDateTime,
    val triggeredAt: LocalDateTime,
    val resolvedAt: LocalDateTime? = null,
    /** Setpoint and threshold (setpoint + tolerance) when it was triggered. */
    val setpoint: Float,
    val threshold: Float,
    /** Current ones: the setpoint may change during an excess. */
    val currentSetpoint: Float? = setpoint,
    /** Hottest box when it was triggered, highest measure since, latest measure. */
    val temperature: Float,
    val peak: Float = temperature,
    val lastTemperature: Float? = temperature,
    val devices: List<AlertDevice> = emptyList(),
    val lastNotifiedAt: LocalDateTime? = null,
    val reminders: Int = 0,
    /** Why it was cancelled (CANCELLED only). */
    val reason: String? = null,
    /** Some message about it could not be delivered (FAILED delivery). */
    val deliveryFailed: Boolean = false,
)

/** What a message tells. */
enum class DeliveryKind { TRIGGERED, REMINDER, RESOLVED, TEST }

enum class DeliveryStatus {
    /** Waiting to be sent, or to be tried again at [AlertDelivery.nextAttemptAt]. */
    PENDING,
    SENT,
    /** Given up: refused for good, or every attempt failed. */
    FAILED,
}

/** One message to one channel, and how sending it went. */
data class AlertDelivery(
    val id: Long? = null,
    /** null for a test. */
    val eventId: Long?,
    val channelId: Long?,
    val channelName: String,
    val kind: DeliveryKind,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: LocalDateTime? = null,
    val lastError: String? = null,
    val createdAt: LocalDateTime? = null,
    val sentAt: LocalDateTime? = null,
)
