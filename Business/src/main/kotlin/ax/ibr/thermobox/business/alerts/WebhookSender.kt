package ax.ibr.thermobox.business.alerts

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

/** Webhooks: Discord, Slack-like ({"text"}) and generic JSON. */
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
            putArray("embeds").addObject().apply {
                put("title", m.subject.removePrefix("[ThermoBox] ").take(256))
                put("description", m.text.take(4096))
                put("color", color(m))
                put("timestamp", m.sentAt.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
                putObject("footer").put("text", "ThermoBox · ${m.values["rule"].orEmpty()}".take(2048))
            }
        }
        WebhookFormat.SLACK -> json.createObjectNode().put("text", "*${m.subject.removePrefix("[ThermoBox] ")}*\n${m.text}")
        WebhookFormat.GENERIC -> json.createObjectNode().apply {
            put("source", "thermobox")
            put("type", "temperature_alert")
            put("kind", m.kind.name)
            put("subject", m.subject)
            put("text", m.text)
            put("sentAt", m.sentAt.toString())
            val e = m.event
            if (e == null) putNull("alert") else putObject("alert").apply {
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

    private fun color(m: AlertMessage): Int = when (m.kind) {
        ax.ibr.thermobox.common.alerts.DeliveryKind.TRIGGERED -> 0xD32F2F
        ax.ibr.thermobox.common.alerts.DeliveryKind.REMINDER -> 0xF57C00
        ax.ibr.thermobox.common.alerts.DeliveryKind.RESOLVED -> 0x388E3C
        ax.ibr.thermobox.common.alerts.DeliveryKind.TEST -> 0x1976D2
    }

    companion object {
        fun hmac(key: String, body: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
