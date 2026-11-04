package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.ChannelKind
import ax.ibr.thermobox.common.alerts.DeliveryKind
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.common.entities.RoomKind
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class ChannelsTest {

    private val now = LocalDateTime.of(2026, 11, 3, 12, 0)
    private val test = AlertMessages.of(DeliveryKind.TEST, null, "Ops", now)

    // ------------------------------------------------------------------ SMTP

    /** An SMTP server that accepts everything and records the dialogue; [features] after EHLO. */
    private class FakeSmtp(private val features: List<String> = listOf("AUTH LOGIN PLAIN"), private val rcptCode: Int = 250) : AutoCloseable {
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val lines = CopyOnWriteArrayList<String>()
        val data = StringBuilder()
        private val worker = thread(isDaemon = true) {
            runCatching {
                socket.accept().use { s ->
                    val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val w = s.getOutputStream()
                    fun say(t: String) { w.write("$t\r\n".toByteArray()); w.flush() }
                    say("220 fake ESMTP")
                    var inData = false
                    while (true) {
                        val line = r.readLine() ?: break
                        if (inData) {
                            if (line == ".") { inData = false; say("250 queued") } else data.append(line).append("\n")
                            continue
                        }
                        lines += line
                        when {
                            line.startsWith("EHLO") -> { say("250-fake"); features.dropLast(1).forEach { say("250-$it") }; say("250 ${features.lastOrNull() ?: "OK"}") }
                            line.startsWith("AUTH LOGIN") -> say("334 VXNlcm5hbWU6")
                            line.startsWith("AUTH PLAIN") -> say("235 ok")
                            line.startsWith("MAIL") -> say("250 ok")
                            line.startsWith("RCPT") -> say("$rcptCode ${if (rcptCode >= 500) "no such user" else "ok"}")
                            line == "DATA" -> { inData = true; say("354 go") }
                            line == "QUIT" -> { say("221 bye"); break }
                            lines.size >= 2 && lines[lines.size - 2].startsWith("AUTH LOGIN") -> say("334 UGFzc3dvcmQ6")
                            lines.size >= 3 && lines[lines.size - 3].startsWith("AUTH LOGIN") -> say("235 ok")
                            else -> say("500 ?")
                        }
                    }
                }
            }
        }
        val port get() = socket.localPort
        override fun close() { socket.close(); worker.join(2000) }
    }

    private fun email(port: Int, username: String? = "alertes", security: SmtpSecurity = SmtpSecurity.NONE) =
        EmailSettings("127.0.0.1", port, security, username, "thermobox@example.org", "ThermoBox Été", listOf("a@example.org", "b@example.org"))

    @Test
    fun `an email with auth, recipients, encoded subject and body, dots doubled`() = FakeSmtp().use { smtp ->
        SmtpClient(Duration.ofSeconds(5)).send(email(smtp.port), "s3cret", "Dépassement : salle", "Ligne 1\n.point\nÉté")
        assertTrue(smtp.lines.any { it == "AUTH PLAIN " + Base64.getEncoder().encodeToString("\u0000alertes\u0000s3cret".toByteArray()) }, smtp.lines.toString())
        assertEquals(listOf("RCPT TO:<a@example.org>", "RCPT TO:<b@example.org>"), smtp.lines.filter { it.startsWith("RCPT") })
        val raw = smtp.data.toString()
        assertTrue("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString("Dépassement : salle".toByteArray()) + "?=" in raw, raw)
        assertTrue("From: =?UTF-8?B?" in raw)
        val body = String(Base64.getMimeDecoder().decode(raw.substringAfter("\n\n").trim()), Charsets.UTF_8)
        assertEquals("Ligne 1\r\n.point\r\nÉté", body)
    }

    @Test
    fun `AUTH LOGIN when PLAIN is not offered, no auth without a user`() {
        FakeSmtp(listOf("AUTH LOGIN")).use { smtp ->
            SmtpClient(Duration.ofSeconds(5)).send(email(smtp.port), "pw", "s", "b")
            assertTrue(smtp.lines.contains("AUTH LOGIN"))
            assertTrue(smtp.lines.contains(Base64.getEncoder().encodeToString("pw".toByteArray())))
        }
        FakeSmtp().use { smtp ->
            SmtpClient(Duration.ofSeconds(5)).send(email(smtp.port, username = null), null, "s", "b")
            assertTrue(smtp.lines.none { it.startsWith("AUTH") })
        }
    }

    @Test
    fun `refusals are permanent for 5xx, STARTTLS required when asked`() {
        FakeSmtp(rcptCode = 550).use { smtp ->
            val e = assertThrows(SmtpException::class.java) { SmtpClient(Duration.ofSeconds(5)).send(email(smtp.port), "pw", "s", "b") }
            assertTrue(e.permanent)
            assertTrue(e.message!!.contains("550"))
        }
        FakeSmtp(listOf("AUTH PLAIN")).use { smtp ->
            val e = assertThrows(SmtpException::class.java) { SmtpClient(Duration.ofSeconds(5)).send(email(smtp.port, security = SmtpSecurity.STARTTLS), "pw", "s", "b") }
            assertTrue(e.message!!.contains("STARTTLS"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SmtpClient().send(email(1).copy(recipients = listOf("x@example.org\r\nRCPT TO:<evil@example.org>")), null, "s", "b")
        }
    }

    // ------------------------------------------------------------------ webhooks

    @Test
    fun `webhooks only to public addresses`() {
        val public = { _: String -> listOf(InetAddress.getByName("162.159.135.232")) }
        assertEquals("discord.com", SafeUrl.check("https://discord.com/api/webhooks/1/abc", false, public).host)
        fun refused(url: String, address: String = "162.159.135.232") =
            assertThrows(IllegalArgumentException::class.java) { SafeUrl.check(url, false) { listOf(InetAddress.getByName(address)) } }.message!!
        assertTrue(refused("http://example.org/hook").contains("HTTPS"))
        assertTrue(refused("ftp://example.org/hook").contains("https://"))
        assertTrue(refused("https://user:pw@example.org/hook").contains("Authorization"))
        for (a in listOf("127.0.0.1", "10.1.2.3", "172.20.0.5", "192.168.1.10", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1")) {
            assertTrue(refused("https://hook.example.org/x", a).contains("private or local"), a)
        }
        // Allowed on purpose (THERMOBOX_ALERTS_ALLOW_PRIVATE_NETWORKS)
        assertEquals("http", SafeUrl.check("http://10.0.0.5:5678/webhook", true).scheme)
    }

    /** A webhook receiver: answers [code] (and [headers]), keeps the requests. */
    private class FakeHook(var code: Int = 204, var headers: Map<String, String> = emptyMap()) : AutoCloseable {
        val bodies = CopyOnWriteArrayList<String>()
        val requestHeaders = CopyOnWriteArrayList<Map<String, String>>()
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/hook") { ex ->
                bodies += ex.requestBody.readBytes().toString(Charsets.UTF_8)
                requestHeaders += ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
                this@FakeHook.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
                ex.sendResponseHeaders(code, -1)
                ex.close()
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}/hook"
        override fun close() = server.stop(0)
    }

    private val json = ObjectMapper()
    private val webhooks = WebhookSender({ true })

    @Test
    fun `Discord, Slack and generic payloads, signed and authorized`() = FakeHook().use { hook ->
        assertEquals(SendResult.Sent, webhooks.send(WebhookSettings(WebhookFormat.DISCORD), ChannelSecrets(url = hook.url), test))
        val discord = json.readTree(hook.bodies.last())
        assertEquals("Test du canal « Ops »", discord.path("embeds")[0].path("title").asText())
        assertTrue(discord.path("allowed_mentions").path("parse").isEmpty)

        webhooks.send(WebhookSettings(WebhookFormat.SLACK), ChannelSecrets(url = hook.url), test)
        assertTrue(json.readTree(hook.bodies.last()).path("text").asText().startsWith("*Test du canal"))

        webhooks.send(WebhookSettings(WebhookFormat.GENERIC), ChannelSecrets(url = hook.url, authorization = "Bearer t0k", signingSecret = "k"), test)
        val body = hook.bodies.last()
        assertEquals("TEST", json.readTree(body).path("kind").asText())
        assertEquals("Bearer t0k", hook.requestHeaders.last()["authorization"])
        assertEquals("sha256=" + WebhookSender.hmac("k", body), hook.requestHeaders.last()["x-thermobox-signature"])
    }

    @Test
    fun `webhook answers mean retry, wait, give up`() = FakeHook().use { hook ->
        val s = WebhookSettings(WebhookFormat.GENERIC)
        hook.code = 503
        assertInstanceOf(SendResult.Retry::class.java, webhooks.send(s, ChannelSecrets(url = hook.url), test))
        hook.code = 429; hook.headers = mapOf("Retry-After" to "12")
        assertEquals(Duration.ofSeconds(12), (webhooks.send(s, ChannelSecrets(url = hook.url), test) as SendResult.Retry).after)
        hook.code = 404; hook.headers = emptyMap()
        assertInstanceOf(SendResult.Failed::class.java, webhooks.send(s, ChannelSecrets(url = hook.url), test))
        hook.code = 302
        assertTrue((webhooks.send(s, ChannelSecrets(url = hook.url), test) as SendResult.Failed).error.contains("redirect"))
        // A private address without the setting: refused before any request
        val strict = WebhookSender({ false })
        val before = hook.bodies.size
        assertInstanceOf(SendResult.Failed::class.java, strict.send(s, ChannelSecrets(url = hook.url), test))
        assertEquals(before, hook.bodies.size)
    }

    // ------------------------------------------------------------------ secrets and administration

    @Test
    fun `secrets are encrypted with a key of the data folder`() {
        val dir = Files.createTempDirectory("tb-secrets")
        val secrets = AlertSecrets(dir.resolve(AlertSecrets.KEY_FILE))
        val sealed = secrets.encrypt("mot de passe")
        assertFalse("mot de passe" in sealed)
        assertEquals("mot de passe", AlertSecrets(dir.resolve(AlertSecrets.KEY_FILE)).decrypt(sealed))
        val other = AlertSecrets(dir.resolve("other.key"), Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        assertThrows(IllegalStateException::class.java) { other.decrypt(sealed) }
        assertThrows(IllegalStateException::class.java) { AlertSecrets(dir.resolve("missing.key")).decrypt(sealed) }
        assertThrows(IllegalStateException::class.java) { AlertSecrets(dir.resolve("x"), "short").encrypt("x") }
    }

    private fun service(store: FakeAlertStore, sender: AlertSender = AlertSender { _, _ -> SendResult.Sent }) = AlertService(
        store, AlertSecrets(Files.createTempDirectory("tb-alerts").resolve("secrets.key")), sender, null, AlertConfig(),
        kindOf = { id -> when (id) { 1L -> RoomKind.BUILDING; 3L -> RoomKind.ROOM; 4L -> RoomKind.DEVICE; else -> null } }, clock = { now },
    )

    @Test
    fun `channel secrets are never shown, kept, replaced or removed`() {
        val store = FakeAlertStore()
        val alerts = service(store)
        val input = ChannelInput("Ops", ChannelKind.EMAIL, host = "smtp.example.org", security = "starttls", username = "u", password = "pw1",
            from = "tb@example.org", recipients = listOf("ops@example.org", " ops@example.org "))
        val created = alerts.createChannel(input)
        assertTrue(created.passwordSet)
        assertNull(created.channel.secret)
        assertEquals(587, created.email!!.port)
        assertEquals(listOf("ops@example.org"), created.email!!.recipients)
        val stored = store.channel(created.channel.id!!)!!
        assertFalse(stored.secret!!.contains("pw1") || stored.settings.contains("pw1"))

        val id = created.channel.id!!
        assertTrue(alerts.updateChannel(id, input.copy(name = "Ops 2", password = null))!!.passwordSet)
        assertFalse(alerts.updateChannel(id, input.copy(password = ""))!!.passwordSet)

        val hook = alerts.createChannel(ChannelInput("Discord", ChannelKind.WEBHOOK, format = "discord",
            url = "https://162.159.135.232/api/webhooks/123/SECRET-TOKEN"))
        assertEquals("https://162.159.135.232/…", hook.webhook!!.urlPreview)
        assertTrue(hook.urlSet)
        assertFalse(store.channel(hook.channel.id!!)!!.settings.contains("SECRET-TOKEN"))
        // The URL is kept when not sent again
        assertTrue(alerts.updateChannel(hook.channel.id!!, ChannelInput("Discord", ChannelKind.WEBHOOK, format = "DISCORD"))!!.urlSet)
    }

    @Test
    fun `invalid channels and rules are refused with the reason`() {
        val alerts = service(FakeAlertStore())
        fun refused(block: () -> Unit) = assertThrows(IllegalArgumentException::class.java) { block() }.message!!
        assertTrue(refused { alerts.createChannel(ChannelInput("x", ChannelKind.EMAIL, host = "smtp", from = "nope", recipients = listOf("a@b.org"))) }.startsWith("from"))
        assertTrue(refused { alerts.createChannel(ChannelInput("x", ChannelKind.EMAIL, host = "smtp", from = "a@b.org")) }.startsWith("recipients"))
        assertTrue(refused { alerts.createChannel(ChannelInput("x", ChannelKind.WEBHOOK, url = "https://127.0.0.1/hook")) }.contains("private or local"))
        assertTrue(refused { alerts.createChannel(ChannelInput("x", ChannelKind.WEBHOOK, format = "teams", url = "https://162.159.135.232/x")) }.startsWith("format"))
        val rule = AlertRule(name = "r", global = false, scope = listOf(3))
        assertNotNull(alerts.createRule(rule).id)
        assertTrue(refused { alerts.createRule(rule.copy(scope = listOf(4))) }.contains("device"))
        assertTrue(refused { alerts.createRule(rule.copy(scope = listOf(99))) }.contains("unknown element"))
        assertTrue(refused { alerts.createRule(rule.copy(scope = emptyList())) }.startsWith("scope"))
        assertTrue(refused { alerts.createRule(rule.copy(tolerance = -1f)) }.startsWith("tolerance"))
        assertTrue(refused { alerts.createRule(rule.copy(reminderSeconds = 60)) }.startsWith("reminderSeconds"))
        assertTrue(refused { alerts.createRule(rule.copy(channels = listOf(42))) }.contains("unknown channel"))
        assertEquals(emptyList<Long>(), alerts.createRule(rule.copy(global = true)).scope)
    }

    @Test
    fun `a test is sent now and kept in the history`() {
        val store = FakeAlertStore()
        val ok = service(store)
        val c = ok.createChannel(ChannelInput("Slack", ChannelKind.WEBHOOK, format = "SLACK", url = "https://162.159.135.232/x"))
        val sent = ok.test(c.channel.id!!)!!
        assertEquals(DeliveryStatus.SENT, sent.status)
        assertEquals(DeliveryKind.TEST, sent.kind)
        val failing = service(store, AlertSender { _, _ -> SendResult.Retry("timeout") })
        val failed = failing.test(c.channel.id!!)!!
        assertEquals(DeliveryStatus.FAILED, failed.status)
        assertEquals("timeout", failed.lastError)
        assertNull(ok.test(999))
    }

    @Test
    fun `at most so many messages a minute per channel`() {
        val secrets = AlertSecrets(Files.createTempDirectory("tb-rate").resolve("k"))
        val sender = ChannelSender(secrets, webhooks = WebhookSender({ true }, timeout = Duration.ofSeconds(2)), perMinute = 2)
        FakeHook().use { hook ->
            val channel = AlertChannel(id = 1, name = "h", kind = ChannelKind.WEBHOOK, settings = ChannelJson.write(WebhookSettings(WebhookFormat.SLACK)),
                secret = secrets.encrypt(ChannelJson.write(ChannelSecrets(url = hook.url))!!))
            assertEquals(SendResult.Sent, sender.send(channel, test))
            assertEquals(SendResult.Sent, sender.send(channel, test))
            assertInstanceOf(SendResult.Postponed::class.java, sender.send(channel, test))
            assertEquals(2, hook.bodies.size)
        }
    }
}
