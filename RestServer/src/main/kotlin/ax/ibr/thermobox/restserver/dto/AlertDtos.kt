package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.business.alerts.AlertOverview
import ax.ibr.thermobox.business.alerts.ChannelInput
import ax.ibr.thermobox.business.alerts.ChannelView
import ax.ibr.thermobox.business.alerts.EventDetail
import ax.ibr.thermobox.business.alerts.MonitorStatus
import ax.ibr.thermobox.business.alerts.RuleRoom
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertDevice
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.kind

/*
 * JSON of /api/v2/alerts (ADMIN). The secrets of a channel (SMTP password, webhook URL,
 * Authorization header, signing key) are only ever received: an answer says whether each one is
 * set (passwordSet, urlSet…), never its value.
 */

data class MonitorStatusDto(
    /** THERMOBOX_ALERTS is not false on this server. */
    val enabled: Boolean,
    val running: Boolean,
    val lastRun: String?,
    val watchedRooms: Int,
    val lastError: String?,
    /** The last run was done by another server sharing the database. */
    val runByAnotherServer: Boolean,
) {
    companion object {
        fun of(s: MonitorStatus) = MonitorStatusDto(s.enabled, s.running, iso(s.lastRun), s.watchedRooms, s.lastError, s.runByAnotherServer)
    }
}

data class EmailChannelDto(
    val host: String,
    val port: Int,
    /** NONE, STARTTLS or SSL. */
    val security: String,
    val username: String?,
    val passwordSet: Boolean,
    val from: String,
    val fromName: String?,
    val recipients: List<String>,
    val template: String?,
)

data class WebhookChannelDto(
    /** DISCORD, SLACK or GENERIC. */
    val format: String,
    /** « https://discord.com/… »: the URL itself is a secret. */
    val urlPreview: String?,
    val urlSet: Boolean,
    val authorizationSet: Boolean,
    val signingSecretSet: Boolean,
    val template: String?,
)

data class AlertChannelDto(
    val id: Long?,
    val name: String,
    /** EMAIL or WEBHOOK. */
    val kind: String,
    val enabled: Boolean,
    val email: EmailChannelDto?,
    val webhook: WebhookChannelDto?,
    /** The stored secrets cannot be read any more (key changed): enter them again. */
    val secretUnreadable: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
) {
    companion object {
        fun of(v: ChannelView) = AlertChannelDto(
            id = v.channel.id, name = v.channel.name, kind = v.channel.kind.name, enabled = v.channel.enabled,
            email = v.email?.let { EmailChannelDto(it.host, it.port, it.security.name, it.username, v.passwordSet, it.from, it.fromName, it.recipients, it.template) },
            webhook = v.webhook?.let { WebhookChannelDto(it.format.name, it.urlPreview, v.urlSet, v.authorizationSet, v.signingSecretSet, it.template) },
            secretUnreadable = v.secretUnreadable,
            createdAt = iso(v.channel.createdAt), updatedAt = iso(v.channel.updatedAt),
        )
    }
}

/**
 * POST / PUT /api/v2/alerts/channels. For each secret (password, url, authorization,
 * signingSecret): absent or null keeps the stored one, "" removes it.
 *
 * {"name": "Astreinte", "kind": "EMAIL", "enabled": true, "email": {"host": "smtp.example.org",
 *  "port": 587, "security": "STARTTLS", "username": "alertes", "password": "…",
 *  "from": "thermobox@example.org", "fromName": "ThermoBox", "recipients": ["astreinte@example.org"]}}
 *
 * {"name": "Discord", "kind": "WEBHOOK", "webhook": {"format": "DISCORD", "url": "https://discord.com/api/webhooks/…"}}
 */
class AlertChannelRequest {
    var name: String = ""
    var kind: String? = null
    var enabled: Boolean = true
    var email: Email? = null
    var webhook: Webhook? = null

    class Email {
        var host: String? = null
        var port: Int? = null
        var security: String? = null
        var username: String? = null
        var password: String? = null
        var from: String? = null
        var fromName: String? = null
        var recipients: List<String> = emptyList()
        var template: String? = null
    }

    class Webhook {
        var format: String? = null
        var url: String? = null
        var authorization: String? = null
        var signingSecret: String? = null
        var template: String? = null
    }

    /** @throws IllegalArgumentException when the kind or its part is missing */
    fun toInput(existingKind: ChannelKind?): ChannelInput {
        val k = kind?.let { v -> ChannelKind.entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) } ?: throw IllegalArgumentException("kind: EMAIL or WEBHOOK") }
            ?: existingKind ?: throw IllegalArgumentException("kind: EMAIL or WEBHOOK")
        return when (k) {
            ChannelKind.EMAIL -> {
                val e = email ?: throw IllegalArgumentException("email: the SMTP settings")
                ChannelInput(name, k, enabled, host = e.host, port = e.port, security = e.security, username = e.username, password = e.password,
                    from = e.from, fromName = e.fromName, recipients = e.recipients, template = e.template)
            }
            ChannelKind.WEBHOOK -> {
                val w = webhook ?: throw IllegalArgumentException("webhook: the webhook settings")
                ChannelInput(name, k, enabled, format = w.format, url = w.url, authorization = w.authorization, signingSecret = w.signingSecret, template = w.template)
            }
        }
    }
}

/** {"enabled": false} */
class EnabledRequest {
    var enabled: Boolean? = null
}

data class AlertElementDto(val id: Long, val name: String?, val kind: String?)

data class AlertRuleDto(
    val id: Long?,
    val name: String,
    val enabled: Boolean,
    /** °C above the setpoint before a measure counts as an excess. */
    val tolerance: Float,
    /** How long a box must stay above before the alert is sent. */
    val minDurationSeconds: Int,
    /** How long every box must stay back under before the alert is resolved. */
    val clearAfterSeconds: Int,
    /** Sent again every … while it lasts (0: never). */
    val reminderSeconds: Int,
    val notifyResolution: Boolean,
    /** The whole installation; otherwise the elements of [scope] and what they contain. */
    val global: Boolean,
    val scope: List<AlertElementDto>,
    val channels: List<AlertElementDto>,
    val createdAt: String?,
    val updatedAt: String?,
) {
    companion object {
        fun of(r: AlertRule, elements: Map<Long, Room>, channelNames: Map<Long, Pair<String, String>>) = AlertRuleDto(
            r.id, r.name, r.enabled, r.tolerance, r.minDurationSeconds, r.clearAfterSeconds, r.reminderSeconds, r.notifyResolution, r.global,
            r.scope.map { id -> elements[id].let { AlertElementDto(id, it?.name, it?.kind?.name) } },
            r.channels.map { id -> channelNames[id].let { AlertElementDto(id, it?.first, it?.second) } },
            iso(r.createdAt), iso(r.updatedAt),
        )
    }
}

/**
 * POST / PUT /api/v2/alerts/rules:
 * {"name": "Salles serveurs", "enabled": true, "tolerance": 1.0, "minDurationSeconds": 600,
 *  "clearAfterSeconds": 600, "reminderSeconds": 3600, "notifyResolution": true,
 *  "global": false, "scope": [12, 15], "channels": [1, 2]}
 */
class AlertRuleRequest {
    var name: String = ""
    var enabled: Boolean = true
    var tolerance: Float = 1f
    var minDurationSeconds: Int = 600
    var clearAfterSeconds: Int = 600
    var reminderSeconds: Int = 0
    var notifyResolution: Boolean = true
    var global: Boolean = false
    var scope: List<Long> = emptyList()
    var channels: List<Long> = emptyList()

    fun toRule() = AlertRule(
        name = name, enabled = enabled, tolerance = tolerance, minDurationSeconds = minDurationSeconds, clearAfterSeconds = clearAfterSeconds,
        reminderSeconds = reminderSeconds, notifyResolution = notifyResolution, global = global, scope = scope, channels = channels,
    )
}

/** GET /api/v2/alerts/rules/{id}/rooms: a room where the rule applies, and where it stands. */
data class RuleRoomDto(
    val id: Long,
    val name: String?,
    /** « Bâtiment A › Étage 1 » */
    val place: String,
    /** null: no setpoint, the room is not watched. */
    val setpoint: Float?,
    val threshold: Float?,
    /** The hottest of its boxes measured in the last 30 min; null when none did. */
    val temperature: Float?,
    val measuredAt: String?,
    /** Above the threshold now. */
    val exceeding: Boolean,
    /** Its alert in progress under this rule. */
    val alertId: Long?,
    val alertSince: String?,
) {
    companion object {
        fun of(r: RuleRoom) = RuleRoomDto(
            r.room.id!!, r.room.name, r.place, r.setpoint, r.threshold, r.temperature, iso(r.measuredAt), r.exceeding,
            r.alert?.id, iso(r.alert?.exceededSince),
        )
    }
}

data class AlertDeviceDto(val id: Long, val name: String?, val temperature: Float?, val at: String?, val exceeding: Boolean) {
    companion object {
        fun of(d: AlertDevice) = AlertDeviceDto(d.id, d.name, d.temperature, iso(d.at), d.exceeding)
    }
}

data class AlertDeliveryDto(
    val id: Long?,
    val eventId: Long?,
    val channelId: Long?,
    val channelName: String,
    /** TRIGGERED, REMINDER, RESOLVED or TEST. */
    val kind: String,
    /** PENDING, SENT or FAILED. */
    val status: String,
    val attempts: Int,
    val nextAttemptAt: String?,
    val lastError: String?,
    val createdAt: String?,
    val sentAt: String?,
) {
    companion object {
        fun of(d: AlertDelivery) = AlertDeliveryDto(d.id, d.eventId, d.channelId, d.channelName, d.kind.name, d.status.name, d.attempts,
            iso(d.nextAttemptAt), d.lastError, iso(d.createdAt), iso(d.sentAt))
    }
}

data class AlertEventDto(
    val id: Long?,
    val ruleId: Long?,
    val ruleName: String,
    val roomId: Long,
    val roomName: String?,
    val place: String,
    /** ACTIVE, RESOLVED or CANCELLED. */
    val state: String,
    val exceededSince: String?,
    val triggeredAt: String?,
    val resolvedAt: String?,
    val setpoint: Float,
    val threshold: Float,
    val currentSetpoint: Float?,
    val temperature: Float,
    val peak: Float,
    val lastTemperature: Float?,
    val devices: List<AlertDeviceDto>,
    val lastNotifiedAt: String?,
    val reminders: Int,
    val reason: String?,
    /** A message about it could not be delivered. */
    val deliveryFailed: Boolean,
    /** Only for GET /events/{id}. */
    val deliveries: List<AlertDeliveryDto>?,
) {
    companion object {
        fun of(e: AlertEvent, deliveries: List<AlertDelivery>? = null) = AlertEventDto(
            e.id, e.ruleId, e.ruleName, e.roomId, e.roomName, e.place, e.state.name, iso(e.exceededSince), iso(e.triggeredAt), iso(e.resolvedAt),
            e.setpoint, e.threshold, e.currentSetpoint, e.temperature, e.peak, e.lastTemperature, e.devices.map { AlertDeviceDto.of(it) },
            iso(e.lastNotifiedAt), e.reminders, e.reason, e.deliveryFailed, deliveries?.map { AlertDeliveryDto.of(it) },
        )

        fun of(d: EventDetail) = of(d.event, d.deliveries)
    }
}

data class AlertOverviewDto(
    val status: MonitorStatusDto,
    val channels: List<AlertChannelDto>,
    val rules: List<AlertRuleDto>,
    val active: List<AlertEventDto>,
    /** The last messages that could not be delivered. */
    val failedDeliveries: List<AlertDeliveryDto>,
) {
    companion object {
        fun of(o: AlertOverview, rule: (AlertRule) -> AlertRuleDto) = AlertOverviewDto(
            MonitorStatusDto.of(o.status), o.channels.map { AlertChannelDto.of(it) }, o.rules.map(rule),
            o.active.map { AlertEventDto.of(it) }, o.failedDeliveries.map { AlertDeliveryDto.of(it) },
        )
    }
}
