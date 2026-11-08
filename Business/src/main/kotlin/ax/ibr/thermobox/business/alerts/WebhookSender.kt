package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.DeliveryKind
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Where a webhook may point. An ADMIN types the URL, but the request leaves from the server: without
 * these checks, an alert channel could make it call what only it can reach (MySQL's port, a cloud
 * metadata service at 169.254.169.254, an administration page of the local network…).
 *
 * - HTTPS only (HTTP too with [allowPrivate]), no user:password in the URL;
 * - every address of the host must be public: no loopback, private (10/8, 172.16/12, 192.168/16,
 *   fc00::/7), link-local (169.254/16, fe80::/10), shared (100.64/10), multicast or unspecified,
 *   unless [allowPrivate] (THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS);
 * - checked again before every request (the host could have been given another address since).
 */
object SafeUrl {

    /** @throws IllegalArgumentException with the reason */
    fun check(url: String, allowPrivate: Boolean, resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }): URI {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (scheme == "http" && allowPrivate)) {
            if (scheme == "http") "Only HTTPS webhooks are accepted (HTTP: THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS=true)" else "The URL must start with https://"
        }
        require(uri.rawUserInfo == null) { "No user name or password in the URL: use the Authorization header" }
        val host = uri.host?.trim('[', ']')?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("The URL has no host")
        require(url.length <= 2048) { "URL too long" }
        if (allowPrivate) return uri
        val addresses = try {
            resolve(host)
        } catch (e: UnknownHostException) {
            throw IllegalArgumentException("Unknown host: $host")
        }
        require(addresses.isNotEmpty()) { "Unknown host: $host" }
        addresses.firstOrNull { !public(it) }?.let {
            throw IllegalArgumentException("$host is on a private or local network (${it.hostAddress}): refused (THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS=true to allow it)")
        }
        return uri
    }

    fun public(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
        val b = a.address.map { it.toInt() and 0xff }
        return when (a) {
            is Inet4Address -> !(b[0] == 0 || b[0] == 10 || (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
                (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 192 && b[1] == 0 && b[2] == 0) ||
                (b[0] == 198 && b[1] in 18..19) || b[0] >= 224)
            is Inet6Address -> {
                // fc00::/7 (unique local), and IPv4-mapped / IPv4-compatible addresses: judged as IPv4
                if ((b[0] and 0xfe) == 0xfc) return false
                val mapped = b.subList(0, 10).all { it == 0 } && ((b[10] == 0xff && b[11] == 0xff) || (b[10] == 0 && b[11] == 0))
                if (mapped) public(InetAddress.getByAddress(a.address.copyOfRange(12, 16))) else true
            }
            else -> false
        }
    }
}

/** How a sending went. */
sealed interface SendResult {
    data object Sent : SendResult
    /** Worth trying again (network, server error, rate limit), at the earliest after [after]. */
    data class Retry(val error: String, val after: Duration? = null) : SendResult
    /** Refused for good: trying again would not help. */
    data class Failed(val error: String) : SendResult
    /** Not attempted (this server's own rate limit): to send after [after], without counting an attempt. */
    data class Postponed(val after: Duration) : SendResult
}

/**
 * Webhooks: Discord (embeds, a field per room), Slack (Block Kit, with its {"text"} for the
 * Slack-like that only read it) and generic JSON. A channel with its own template sends its text.
 */
class WebhookSender(
    private val allowPrivate: () -> Boolean,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        // A redirect could lead anywhere, past the checks of SafeUrl
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val timeout: Duration = Duration.ofSeconds(15),
) {
    private val json = ObjectMapper()

    fun send(settings: WebhookSettings, secrets: ChannelSecrets, message: AlertMessage): SendResult {
        val url = secrets.url ?: return SendResult.Failed("No webhook URL")
        val uri = try {
            SafeUrl.check(url, allowPrivate())
        } catch (e: IllegalArgumentException) {
            return SendResult.Failed(e.message ?: "URL refused")
        }
        val body = json.writeValueAsString(payload(settings, message))
        val request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", "ThermoBox-Alerts")
            .header("X-ThermoBox-Event", message.kind.name)
            .apply {
                secrets.authorization?.takeIf { it.isNotBlank() }?.let { header("Authorization", it) }
                secrets.signingSecret?.takeIf { it.isNotBlank() }?.let { header("X-ThermoBox-Signature", "sha256=" + hmac(it, body)) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            return SendResult.Retry("${uri.host} unreachable: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return SendResult.Retry("Interrupted")
        }
        val code = response.statusCode()
        val detail = response.body().orEmpty().replace(Regex("\\s+"), " ").take(200)
        return when {
            code in 200..299 -> SendResult.Sent
            code == 429 -> SendResult.Retry("$code: rate limited by ${uri.host}", retryAfter(response))
            code == 408 || code >= 500 -> SendResult.Retry("$code from ${uri.host} $detail".trim())
            code in 300..399 -> SendResult.Failed("$code: the webhook redirects (not followed), give its final URL")
            else -> SendResult.Failed("$code from ${uri.host} $detail".trim())
        }
    }

    private fun retryAfter(r: HttpResponse<String>): Duration? =
        r.headers().firstValue("Retry-After").orElse(null)?.trim()?.toDoubleOrNull()?.let { Duration.ofMillis((it * 1000).toLong().coerceIn(1000, 3_600_000)) }

    internal fun payload(settings: WebhookSettings, m: AlertMessage): ObjectNode = when (settings.format) {
        WebhookFormat.DISCORD -> json.createObjectNode().apply {
            put("username", "ThermoBox")
            // Never notify @everyone or a role from a room name
            putObject("allowed_mentions").putArray("parse")
            val embeds = putArray("embeds")
            val rooms = m.items.filter { it.event != null }
            when {
                // The channel's own template, or a test: its text as it is
                m.templated || rooms.isEmpty() -> embeds.addObject().apply {
                    put("title", m.subject.removePrefix("[ThermoBox] ").take(256))
                    put("description", m.text.take(4096))
                    put("color", color(m.kind))
                    put("timestamp", timestamp(m))
                    putObject("footer").put("text", "ThermoBox · ${rules(m)}".take(2048))
                }
                rooms.size == 1 -> embeds.add(discordOne(m, rooms.single()))
                else -> discordMany(m).forEach { embeds.add(it) }
            }
        }
        WebhookFormat.SLACK -> json.createObjectNode().apply {
            // The text: the notification, and what the Slack-like (Mattermost, Rocket.Chat…) show
            put("text", "*${m.subject.removePrefix("[ThermoBox] ")}*\n${m.text}")
            if (!m.templated && m.items.count { it.event != null } > 1) set<ObjectNode>("blocks", slackBlocks(m))
        }
        WebhookFormat.GENERIC -> json.createObjectNode().apply {
            put("source", "thermobox")
            put("type", "temperature_alert")
            put("kind", m.kind.name)
            put("subject", m.subject)
            put("text", m.text)
            put("sentAt", m.sentAt.toString())
            // Every room of the message (none for a test), and which part it is when split
            put("count", m.items.count { it.event != null })
            put("part", m.part)
            put("parts", m.parts)
            putArray("alerts").apply {
                m.items.forEach { item ->
                    val e = item.event ?: return@forEach
                    addObject().apply {
                        put("kind", item.kind.name)
                        e.id?.let { put("id", it) }
                        put("state", e.state.name)
                        e.ruleId?.let { put("ruleId", it) }
                        put("rule", e.ruleName)
                        putObject("room").apply { put("id", e.roomId); put("name", e.roomName); put("place", e.place) }
                        put("temperature", e.lastTemperature ?: e.temperature)
                        put("peak", e.peak)
                        put("setpoint", e.currentSetpoint ?: e.setpoint)
                        put("threshold", e.threshold)
                        put("exceededSince", e.exceededSince.toString())
                        put("triggeredAt", e.triggeredAt.toString())
                        e.resolvedAt?.let { put("resolvedAt", it.toString()) }
                        putArray("devices").apply {
                            e.devices.forEach { d ->
                                addObject().apply {
                                    put("id", d.id); put("name", d.name); put("exceeding", d.exceeding)
                                    d.temperature?.let { put("temperature", it) }
                                    d.at?.let { put("at", it.toString()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Discord, one room: the title says what and where, the temperature in large, then the facts
     * side by side (fields), the boxes over the threshold below.
     */
    private fun discordOne(m: AlertMessage, item: AlertItem): ObjectNode = json.createObjectNode().apply {
        val v = item.values
        put("title", m.subject.removePrefix("[ThermoBox] ").take(256))
        put("description", when (item.kind) {
            DeliveryKind.RESOLVED -> "La température de **${v["room"]}** est revenue sous le seuil : **${v["temperature"]}**."
            DeliveryKind.REMINDER -> "La température de **${v["room"]}** dépasse toujours le seuil : **${v["temperature"]}**."
            else -> "La température de **${v["room"]}** dépasse le seuil : **${v["temperature"]}**."
        }.take(4096))
        put("color", color(item.kind))
        put("timestamp", timestamp(m))
        val fields = putArray("fields")
        fun field(name: String, value: String?, inline: Boolean = true) {
            if (!value.isNullOrBlank()) fields.addObject().put("name", name.take(256)).put("value", value.take(1024)).put("inline", inline)
        }
        field("Lieu", v["place"], inline = false)
        field("Consigne", v["setpoint"])
        field("Seuil", v["threshold"])
        field("Maximum", v["peak"])
        field(if (item.kind == DeliveryKind.RESOLVED) "Dépassement" else "Depuis", item.event?.exceededSince?.let { AlertMessages.at(it, m.sentAt) + " (${v["duration"]})" } ?: v["since"])
        field("Boîtiers", v["devices"]?.split(", ")?.joinToString("\n"), inline = false)
        putObject("footer").put("text", "ThermoBox · règle ${v["rule"]}".take(2048))
    }

    /**
     * Discord, several rooms: a first embed that sums up, then one embed per section (in its
     * colour), a field per room: its name and temperature, then where and since when. Within the
     * limits of Discord (10 embeds, 25 fields each, 6000 characters in all): past them, the text.
     */
    private fun discordMany(m: AlertMessage): List<ObjectNode> {
        val rule = AlertMessages.sharedRule(m.items)
        val head = json.createObjectNode().apply {
            put("title", m.subject.removePrefix("[ThermoBox] ").take(256))
            put("color", color(m.kind))
            if (rule != null) put("description", "Règle : **$rule**")
        }
        val embeds = arrayListOf(head)
        var size = m.subject.length + (rule?.length ?: 0) + 40
        for ((kind, items) in AlertMessages.sections(m.items)) {
            val embed = json.createObjectNode().apply {
                put("title", "${AlertMessages.sectionTitle(kind)} (${items.size})")
                put("color", color(kind))
            }
            val fields = embed.putArray("fields")
            items.take(25).forEach { item ->
                val line = AlertMessages.roomLine(item, m.sentAt, rule == null)
                val name = "${line.room} · ${line.temperature} (${line.note})".take(256)
                val value = line.detail.ifEmpty { "—" }.take(1024)
                fields.addObject().put("name", name).put("value", value).put("inline", false)
                size += name.length + value.length
            }
            size += 60
            embeds += embed
        }
        embeds.last().put("timestamp", timestamp(m)).putObject("footer").put("text", if (m.parts > 1) "ThermoBox · partie ${m.part}/${m.parts}" else "ThermoBox")
        if (embeds.size > 10 || size > 5800) {
            return listOf(json.createObjectNode().apply {
                put("title", m.subject.removePrefix("[ThermoBox] ").take(256))
                put("description", m.text.take(4096))
                put("color", color(m.kind))
                put("timestamp", timestamp(m))
            })
        }
        return embeds
    }

    /** Slack (Block Kit): a header, a title per section, a block per room, the rule and the date. */
    private fun slackBlocks(m: AlertMessage): com.fasterxml.jackson.databind.node.ArrayNode {
        val rule = AlertMessages.sharedRule(m.items)
        val blocks = json.createArrayNode()
        blocks.addObject().put("type", "header").putObject("text").put("type", "plain_text").put("text", m.subject.removePrefix("[ThermoBox] ").take(150))
        AlertMessages.sections(m.items).forEachIndexed { i, (kind, items) ->
            if (i > 0) blocks.addObject().put("type", "divider")
            blocks.addObject().put("type", "section").putObject("text").put("type", "mrkdwn")
                .put("text", "${emoji(kind)} *${AlertMessages.sectionTitle(kind)}* (${items.size})")
            items.forEach { item ->
                val line = AlertMessages.roomLine(item, m.sentAt, rule == null)
                blocks.addObject().put("type", "section").putObject("text").put("type", "mrkdwn")
                    .put("text", "*${escape(line.room)}* · ${line.temperature}  _(${escape(line.note)})_\n${escape(line.detail)}".take(3000))
            }
        }
        val context = listOfNotNull(rule?.let { "Règle : ${escape(it)}" }, if (m.parts > 1) "partie ${m.part}/${m.parts}" else null, "ThermoBox").joinToString(" · ")
        blocks.addObject().put("type", "context").putArray("elements").addObject().put("type", "mrkdwn").put("text", context)
        return blocks
    }

    /** Slack's mrkdwn: & < > are escaped, the rest as written. */
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun emoji(k: DeliveryKind) = when (k) {
        DeliveryKind.TRIGGERED -> ":red_circle:"
        DeliveryKind.REMINDER -> ":large_orange_circle:"
        DeliveryKind.RESOLVED -> ":large_green_circle:"
        DeliveryKind.TEST -> ":large_blue_circle:"
    }

    private fun timestamp(m: AlertMessage) = m.sentAt.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

    /** The rules of the alerts of [m], for the footer of Discord. */
    private fun rules(m: AlertMessage): String = m.items.mapNotNull { it.event?.ruleName }.distinct().joinToString(", ").ifEmpty { m.values["rule"].orEmpty() }

    private fun color(k: DeliveryKind): Int = when (k) {
        DeliveryKind.TRIGGERED -> 0xD32F2F
        DeliveryKind.REMINDER -> 0xF57C00
        DeliveryKind.RESOLVED -> 0x388E3C
        DeliveryKind.TEST -> 0x1976D2
    }

    companion object {
        fun hmac(key: String, body: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
