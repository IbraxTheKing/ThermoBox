package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.persistence.dataservices.AlertDataService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * A channel as an administrator sees it: its settings, and only whether each secret is set
 * (never its value).
 */
data class ChannelView(
    val channel: AlertChannel,
    val email: EmailSettings?,
    val webhook: WebhookSettings?,
    val passwordSet: Boolean,
    val urlSet: Boolean,
    val authorizationSet: Boolean,
    val signingSecretSet: Boolean,
    /** The stored secrets cannot be read with the current key (they must be entered again). */
    val secretUnreadable: Boolean,
)

/**
 * What an administrator sends for a channel. For each secret: null keeps the stored one, "" removes
 * it, any other value replaces it.
 */
data class ChannelInput(
    val name: String,
    val kind: ChannelKind,
    val enabled: Boolean = true,
    // Email
    val host: String? = null,
    val port: Int? = null,
    val security: String? = null,
    val username: String? = null,
    val password: String? = null,
    val from: String? = null,
    val fromName: String? = null,
    val recipients: List<String> = emptyList(),
    // Webhook
    val format: String? = null,
    val url: String? = null,
    val authorization: String? = null,
    val signingSecret: String? = null,
    // Both
    val template: String? = null,
)

/** An alert with the messages sent about it. */
data class EventDetail(val event: AlertEvent, val deliveries: List<AlertDelivery>)

/** Everything at once, for the alerts page of a client. */
data class AlertOverview(
    val status: MonitorStatus,
    val channels: List<ChannelView>,
    val rules: List<AlertRule>,
    val active: List<AlertEvent>,
    val failedDeliveries: List<AlertDelivery>,
)

/**
 * Administration of the temperature alerts (ADMIN, through /api/v2/alerts): channels, rules,
 * tests and history. Every value is checked here; an invalid one is an IllegalArgumentException
 * with the reason, nothing saved.
 */
class AlertService(
    private val store: AlertDataService,
    private val secrets: AlertSecrets,
    private val sender: AlertSender,
    private val monitor: AlertMonitor?,
    private val config: AlertConfig,
    /** Kind of an element of the hierarchy, null if it does not exist. */
    private val kindOf: (Long) -> RoomKind?,
    private val clock: () -> LocalDateTime = { LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) },
) {

    fun overview() = AlertOverview(
        status = status(),
        channels = channels(),
        rules = store.rules(),
        active = store.activeEvents(),
        failedDeliveries = store.deliveries(DeliveryStatus.FAILED, 20),
    )

    fun status(): MonitorStatus = monitor?.status() ?: MonitorStatus(config.enabled, false, null, 0, null, false)

    // ------------------------------------------------------------------ channels

    fun channels(): List<ChannelView> = store.channels().map { view(it) }

    fun channel(id: Long): ChannelView? = store.channel(id)?.let { view(it) }

    fun createChannel(input: ChannelInput): ChannelView = view(store.save(build(null, input)))

    fun updateChannel(id: Long, input: ChannelInput): ChannelView? {
        val current = store.channel(id) ?: return null
        return view(store.save(build(current, input)))
    }

    /** On or off, nothing else changed. */
    fun enableChannel(id: Long, enabled: Boolean): ChannelView? {
        val current = store.channel(id) ?: return null
        return view(store.save(current.copy(enabled = enabled)))
    }

    fun deleteChannel(id: Long): Boolean = store.deleteChannel(id)

    /**
     * Sends a test message through the channel now (even disabled), and keeps it in the history.
     * The delivery tells how it went (SENT, or FAILED with the error).
     */
    fun test(id: Long): AlertDelivery? {
        val channel = store.channel(id) ?: return null
        val now = clock()
        val result = sender.send(channel, AlertMessages.of(DeliveryKind.TEST, null, channel.name, now))
        val d = AlertDelivery(eventId = null, channelId = channel.id, channelName = channel.name, kind = DeliveryKind.TEST, createdAt = now)
        return store.save(when (result) {
            SendResult.Sent -> d.copy(status = DeliveryStatus.SENT, attempts = 1, sentAt = now)
            is SendResult.Failed -> d.copy(status = DeliveryStatus.FAILED, attempts = 1, lastError = result.error)
            is SendResult.Retry -> d.copy(status = DeliveryStatus.FAILED, attempts = 1, lastError = result.error)
            is SendResult.Postponed -> d.copy(status = DeliveryStatus.FAILED, lastError = "Too many messages through this channel in the last minute: try again in ${result.after.seconds} s")
        })
    }

    private fun build(current: AlertChannel?, i: ChannelInput): AlertChannel {
        val name = i.name.trim()
        require(name.isNotEmpty() && name.length <= 100) { "name: 1 to 100 characters" }
        require(current == null || current.kind == i.kind) { "kind cannot change: create another channel" }
        i.template?.let { require(it.length <= 4000) { "template: 4000 characters at most" } }
        val old = current?.secret?.let { runCatching { ChannelJson.secrets(secrets.decrypt(it)) }.getOrNull() } ?: ChannelSecrets()
        fun keep(new: String?, old: String?): String? = when (new) {
            null -> old
            "" -> null
            else -> new
        }
        return when (i.kind) {
            ChannelKind.EMAIL -> {
                val security = parse<SmtpSecurity>(i.security ?: "STARTTLS", "security")
                val host = i.host?.trim().orEmpty()
                require(host.isNotEmpty() && host.length <= 255 && host.none { it.isWhitespace() || it == '/' }) { "host: the name or address of the SMTP server" }
                val port = i.port ?: security.defaultPort
                require(port in 1..65535) { "port: 1 to 65535" }
                val from = i.from?.trim().orEmpty()
                require(SmtpClient.Addresses.valid(from)) { "from: a valid email address" }
                val recipients = i.recipients.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                require(recipients.isNotEmpty()) { "recipients: at least one address" }
                require(recipients.size <= 50) { "recipients: 50 at most" }
                recipients.firstOrNull { !SmtpClient.Addresses.valid(it) }?.let { throw IllegalArgumentException("recipients: invalid address $it") }
                i.fromName?.let { require(it.length <= 100 && it.none { c -> c == '\r' || c == '\n' }) { "fromName: one line, 100 characters at most" } }
                val username = i.username?.trim()?.takeIf { it.isNotEmpty() }
                require(username == null || username.length <= 255) { "username: 255 characters at most" }
                val settings = EmailSettings(host, port, security, username, from, i.fromName?.trim()?.takeIf { it.isNotEmpty() }, recipients,
                    i.template?.takeIf { it.isNotBlank() })
                val s = ChannelSecrets(password = keep(i.password, old.password))
                AlertChannel(current?.id, name, i.kind, i.enabled, ChannelJson.write(settings), ChannelJson.write(s)?.let { secrets.encrypt(it) })
            }
            ChannelKind.WEBHOOK -> {
                val format = parse<WebhookFormat>(i.format ?: "GENERIC", "format")
                val url = keep(i.url?.trim(), old.url) ?: throw IllegalArgumentException("url: the address of the webhook")
                if (i.url != null) SafeUrl.check(url, config.allowPrivateNetworks)
                i.authorization?.let { require(it.length <= 1000 && it.none { c -> c == '\r' || c == '\n' }) { "authorization: one line" } }
                val settings = WebhookSettings(format, i.template?.takeIf { it.isNotBlank() }, ChannelJson.preview(url))
                val s = ChannelSecrets(url = url, authorization = keep(i.authorization, old.authorization), signingSecret = keep(i.signingSecret, old.signingSecret))
                AlertChannel(current?.id, name, i.kind, i.enabled, ChannelJson.write(settings), ChannelJson.write(s)?.let { secrets.encrypt(it) })
            }
        }
    }

    private fun view(c: AlertChannel): ChannelView {
        val read = c.secret?.let { runCatching { ChannelJson.secrets(secrets.decrypt(it)) } }
        val s = read?.getOrNull() ?: ChannelSecrets()
        return ChannelView(
            channel = c.copy(secret = null),
            email = if (c.kind == ChannelKind.EMAIL) ChannelJson.email(c.settings) else null,
            webhook = if (c.kind == ChannelKind.WEBHOOK) ChannelJson.webhook(c.settings) else null,
            passwordSet = s.password != null, urlSet = s.url != null,
            authorizationSet = s.authorization != null, signingSecretSet = s.signingSecret != null,
            secretUnreadable = read?.isFailure == true,
        )
    }

    // ------------------------------------------------------------------ rules

    fun rules(): List<AlertRule> = store.rules()

    fun rule(id: Long): AlertRule? = store.rule(id)

    fun createRule(rule: AlertRule): AlertRule = store.save(checked(rule.copy(id = null)))

    fun updateRule(id: Long, rule: AlertRule): AlertRule? {
        store.rule(id) ?: return null
        return store.save(checked(rule.copy(id = id)))
    }

    fun enableRule(id: Long, enabled: Boolean): AlertRule? = store.rule(id)?.let { store.save(it.copy(enabled = enabled)) }

    fun deleteRule(id: Long): Boolean = store.deleteRule(id)

    private fun checked(r: AlertRule): AlertRule {
        val name = r.name.trim()
        require(name.isNotEmpty() && name.length <= 100) { "name: 1 to 100 characters" }
        require(r.tolerance.isFinite() && r.tolerance in 0f..20f) { "tolerance: 0 to 20 °C above the setpoint" }
        require(r.minDurationSeconds in 0..MAX_DURATION) { "minDurationSeconds: 0 to $MAX_DURATION (6 h)" }
        require(r.clearAfterSeconds in 0..MAX_DURATION) { "clearAfterSeconds: 0 to $MAX_DURATION (6 h)" }
        require(r.reminderSeconds == 0 || r.reminderSeconds in MIN_REMINDER..MAX_REMINDER) { "reminderSeconds: 0 (no reminder), or $MIN_REMINDER (5 min) to $MAX_REMINDER (7 days)" }
        val scope = r.scope.distinct()
        if (!r.global) {
            require(scope.isNotEmpty()) { "scope: at least one building, floor or room (or global: true)" }
            for (id in scope) {
                val kind = kindOf(id) ?: throw IllegalArgumentException("scope: unknown element $id")
                require(kind != RoomKind.DEVICE) { "scope: $id is a device; choose its room" }
            }
        }
        val channels = r.channels.distinct()
        val known = store.channels().mapNotNull { it.id }.toSet()
        channels.firstOrNull { it !in known }?.let { throw IllegalArgumentException("channels: unknown channel $it") }
        return r.copy(name = name, scope = if (r.global) emptyList() else scope, channels = channels)
    }

    // ------------------------------------------------------------------ history

    fun events(state: AlertState?, roomId: Long?, ruleId: Long?, before: Long?, limit: Int): List<AlertEvent> =
        store.events(state, roomId, ruleId, before, limit)

    fun event(id: Long): EventDetail? = store.event(id)?.let { EventDetail(it, store.deliveries(id)) }

    fun deliveries(status: DeliveryStatus?, limit: Int): List<AlertDelivery> = store.deliveries(status, limit)

    private inline fun <reified E : Enum<E>> parse(value: String, field: String): E =
        enumValues<E>().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("$field: one of ${enumValues<E>().joinToString { it.name }}")

    companion object {
        const val MAX_DURATION = 6 * 3600
        const val MIN_REMINDER = 300
        const val MAX_REMINDER = 7 * 24 * 3600
    }
}
