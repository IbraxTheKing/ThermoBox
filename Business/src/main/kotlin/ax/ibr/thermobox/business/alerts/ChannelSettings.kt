package ax.ibr.thermobox.business.alerts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI

/** How the SMTP connection is protected. */
enum class SmtpSecurity(val defaultPort: Int) {
    /** Plain text (a relay on the local network). */
    NONE(25),
    /** Plain text, then TLS before anything else (submission, port 587). */
    STARTTLS(587),
    /** TLS from the start (port 465). */
    SSL(465),
}

/** What the webhook expects. */
enum class WebhookFormat {
    /** A Discord webhook (https://discord.com/api/webhooks/…): a message with an embed. */
    DISCORD,
    /** Slack, Mattermost, Rocket.Chat, Microsoft Teams (workflows)…: {"text": "…"}. */
    SLACK,
    /** Any service: the whole alert in JSON (n8n, Zapier, Home Assistant, a script…). */
    GENERIC,
}

/** What may be shown of an email channel. */
data class EmailSettings(
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val username: String? = null,
    val from: String,
    val fromName: String? = null,
    val recipients: List<String>,
    /** Text of the message instead of the default one, with {placeholders} (AlertMessages). */
    val template: String? = null,
)

/** What may be shown of a webhook channel: its URL is a secret (a Discord URL holds its token). */
data class WebhookSettings(
    val format: WebhookFormat,
    val template: String? = null,
    /** « https://discord.com/… »: enough to recognize it. */
    val urlPreview: String? = null,
)

/** Secrets of a channel, never sent back by the API. */
data class ChannelSecrets(
    /** SMTP password. */
    val password: String? = null,
    /** Webhook URL. */
    val url: String? = null,
    /** Value of the Authorization header sent with a webhook (« Bearer … »). */
    val authorization: String? = null,
    /** Key of the HMAC-SHA256 signature of a generic webhook (X-ThermoBox-Signature). */
    val signingSecret: String? = null,
)

/** Reading and writing of the JSON kept in AlertChannel.settings and (encrypted) AlertChannel.secret. */
object ChannelJson {
    private val json = ObjectMapper()

    fun email(text: String): EmailSettings {
        val n = json.readTree(text)
        val security = runCatching { SmtpSecurity.valueOf(n.str("security") ?: "STARTTLS") }.getOrDefault(SmtpSecurity.STARTTLS)
        return EmailSettings(
            host = n.str("host").orEmpty(),
            port = n.path("port").asInt(security.defaultPort),
            security = security,
            username = n.str("username"),
            from = n.str("from").orEmpty(),
            fromName = n.str("fromName"),
            recipients = n.path("recipients").mapNotNull { it.asText().takeIf { s -> s.isNotBlank() } },
            template = n.str("template"),
        )
    }

    fun write(s: EmailSettings): String = json.writeValueAsString(json.createObjectNode().apply {
        put("host", s.host); put("port", s.port); put("security", s.security.name)
        s.username?.let { put("username", it) }
        put("from", s.from)
        s.fromName?.let { put("fromName", it) }
        putArray("recipients").apply { s.recipients.forEach { add(it) } }
        s.template?.let { put("template", it) }
    })

    fun webhook(text: String): WebhookSettings {
        val n = json.readTree(text)
        return WebhookSettings(
            format = runCatching { WebhookFormat.valueOf(n.str("format") ?: "GENERIC") }.getOrDefault(WebhookFormat.GENERIC),
            template = n.str("template"),
            urlPreview = n.str("urlPreview"),
        )
    }

    fun write(s: WebhookSettings): String = json.writeValueAsString(json.createObjectNode().apply {
        put("format", s.format.name)
        s.template?.let { put("template", it) }
        s.urlPreview?.let { put("urlPreview", it) }
    })

    fun secrets(text: String?): ChannelSecrets {
        if (text.isNullOrBlank()) return ChannelSecrets()
        val n = json.readTree(text)
        return ChannelSecrets(n.str("password"), n.str("url"), n.str("authorization"), n.str("signingSecret"))
    }

    /** null when there is no secret at all. */
    fun write(s: ChannelSecrets): String? {
        val n: ObjectNode = json.createObjectNode()
        s.password?.let { n.put("password", it) }
        s.url?.let { n.put("url", it) }
        s.authorization?.let { n.put("authorization", it) }
        s.signingSecret?.let { n.put("signingSecret", it) }
        return if (n.isEmpty) null else json.writeValueAsString(n)
    }

    /** « https://discord.com/… »: scheme and host, without the path that holds the token. */
    fun preview(url: String): String = runCatching {
        val u = URI(url)
        "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}/…"
    }.getOrDefault("…")

    private fun JsonNode.str(field: String): String? = path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotEmpty() }
}
