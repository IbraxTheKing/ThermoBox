package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.ChannelKind
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** Sends a message through a channel, whatever its kind. */
fun interface AlertSender {
    fun send(channel: AlertChannel, message: AlertMessage): SendResult
}

/**
 * The channels of 26.12: email (SMTP) and webhooks. Another kind is a new [ChannelKind], its
 * settings in [ChannelJson] and a branch here.
 *
 * At most [perMinute] messages a minute per channel (Discord accepts about 30): beyond, the next
 * ones are postponed, not lost.
 */
class ChannelSender(
    private val secrets: AlertSecrets,
    private val smtp: SmtpClient = SmtpClient(),
    private val webhooks: WebhookSender,
    private val perMinute: Int = 20,
    private val clock: Clock = Clock.systemUTC(),
) : AlertSender {

    private val recent = ConcurrentHashMap<Long, ArrayDeque<Instant>>()

    override fun send(channel: AlertChannel, message: AlertMessage): SendResult {
        limit(channel)?.let { return it }
        val secret = try {
            ChannelJson.secrets(channel.secret?.let { secrets.decrypt(it) })
        } catch (e: IllegalStateException) {
            return SendResult.Failed(e.message ?: "Secret unreadable")
        }
        return when (channel.kind) {
            ChannelKind.EMAIL -> email(ChannelJson.email(channel.settings), secret, message)
            // The template of the channel is already in the text (AlertMessages, once per room)
            ChannelKind.WEBHOOK -> webhooks.send(ChannelJson.webhook(channel.settings), secret, message)
        }
    }

    private fun email(settings: EmailSettings, secret: ChannelSecrets, message: AlertMessage): SendResult {
        return try {
            smtp.send(settings, secret.password, message.subject, message.text + FOOTER)
            SendResult.Sent
        } catch (e: SmtpException) {
            if (e.permanent) SendResult.Failed(e.message ?: "Refused by the SMTP server") else SendResult.Retry(e.message ?: "SMTP error")
        } catch (e: IllegalArgumentException) {
            SendResult.Failed(e.message ?: "Invalid settings")
        } catch (e: javax.net.ssl.SSLException) {
            // Certificate refused, TLS version…: the same next time
            SendResult.Failed("TLS with ${settings.host}: ${e.message}")
        } catch (e: IOException) {
            SendResult.Retry("${settings.host}:${settings.port} unreachable: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Postponed when the channel already sent [perMinute] messages in the last minute. */
    private fun limit(channel: AlertChannel): SendResult? {
        val id = channel.id ?: return null
        val now = clock.instant()
        val q = recent.computeIfAbsent(id) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && Duration.between(q.peekFirst(), now) >= MINUTE) q.pollFirst()
            if (q.size >= perMinute) return SendResult.Postponed(MINUTE.minus(Duration.between(q.peekFirst(), now)).plusSeconds(1))
            q.addLast(now)
        }
        return null
    }

    private companion object {
        val MINUTE: Duration = Duration.ofMinutes(1)
        const val FOOTER = "\n\n--\nMessage automatique de ThermoBox (alertes de température)."
    }
}
