package ax.ibr.thermobox.business.alerts

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** The SMTP server refused (or the dialogue failed); [permanent]: a 5xx answer, trying again would not help. */
class SmtpException(message: String, val permanent: Boolean) : IOException(message)

/**
 * Just enough SMTP to send a text message (RFC 5321): plain, STARTTLS or TLS from the start,
 * AUTH PLAIN or LOGIN, several recipients, UTF-8 subject and body. The certificate of the server
 * and its name are checked as a browser does; STARTTLS is required when asked for (no silent
 * fallback to plain text).
 */
class SmtpClient(
    private val timeout: Duration = Duration.ofSeconds(20),
    private val sslFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val clientName: String = "thermobox",
) {

    fun send(settings: EmailSettings, password: String?, subject: String, body: String) {
        val recipients = settings.recipients
        require(recipients.isNotEmpty()) { "No recipient" }
        (listOf(settings.from) + recipients).forEach { require(Addresses.valid(it)) { "Invalid address: $it" } }

        var socket: Socket = connect(settings)
        try {
            var io = Io(socket)
            io.expect(220)
            var features = io.command("EHLO $clientName", 250)
            if (settings.security == SmtpSecurity.STARTTLS) {
                if (features.none { it.uppercase().startsWith("STARTTLS") }) {
                    throw SmtpException("${settings.host} does not offer STARTTLS: choose SSL, or NONE on a trusted network", true)
                }
                io.command("STARTTLS", 220)
                socket = tls(socket, settings.host, wrap = true)
                io = Io(socket)
                features = io.command("EHLO $clientName", 250)
            }
            val user = settings.username?.takeIf { it.isNotBlank() }
            if (user != null) authenticate(io, features, user, password.orEmpty())
            io.command("MAIL FROM:<${settings.from}>", 250)
            for (r in recipients) io.command("RCPT TO:<$r>", 250, 251)
            io.command("DATA", 354)
            io.write(dotStuffed(message(settings, subject, body)) + "\r\n.\r\n")
            io.expect(250)
            runCatching { io.command("QUIT", 221) }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun connect(s: EmailSettings): Socket {
        val plain = Socket()
        plain.connect(InetSocketAddress(s.host, s.port), timeout.toMillis().toInt())
        plain.soTimeout = timeout.toMillis().toInt()
        return if (s.security == SmtpSecurity.SSL) tls(plain, s.host, wrap = true) else plain
    }

    private fun tls(socket: Socket, host: String, wrap: Boolean): Socket {
        val ssl = sslFactory.createSocket(socket, host, socket.port, wrap) as SSLSocket
        ssl.sslParameters = ssl.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
            if (!host.first().isDigit() && ':' !in host) serverNames = listOf(SNIHostName(host))
        }
        ssl.soTimeout = timeout.toMillis().toInt()
        ssl.startHandshake()
        return ssl
    }

    private fun authenticate(io: Io, features: List<String>, user: String, password: String) {
        val auth = features.firstOrNull { it.uppercase().startsWith("AUTH") }?.uppercase()?.split(' ', '=').orEmpty()
        val b64 = Base64.getEncoder()
        when {
            "PLAIN" in auth || auth.isEmpty() ->
                io.command("AUTH PLAIN " + b64.encodeToString("\u0000$user\u0000$password".toByteArray(Charsets.UTF_8)), 235, secret = true)
            "LOGIN" in auth -> {
                io.command("AUTH LOGIN", 334)
                io.command(b64.encodeToString(user.toByteArray(Charsets.UTF_8)), 334, secret = true)
                io.command(b64.encodeToString(password.toByteArray(Charsets.UTF_8)), 235, secret = true)
            }
            else -> throw SmtpException("The server offers neither AUTH PLAIN nor AUTH LOGIN (${auth.drop(1).joinToString(" ")})", true)
        }
    }

    internal fun message(s: EmailSettings, subject: String, body: String): String {
        val from = s.fromName?.takeIf { it.isNotBlank() }?.let { "${encoded(it)} <${s.from}>" } ?: s.from
        val headers = listOf(
            "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ROOT).format(ZonedDateTime.now()),
            "From: $from",
            "To: " + s.recipients.joinToString(", "),
            "Subject: " + encoded(subject),
            "Message-ID: <${UUID.randomUUID()}@$clientName>",
            "MIME-Version: 1.0",
            "Content-Type: text/plain; charset=UTF-8",
            "Content-Transfer-Encoding: base64",
            "Auto-Submitted: auto-generated",
        )
        val encodedBody = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
        return headers.joinToString("\r\n") + "\r\n\r\n" + encodedBody
    }

    /** RFC 2047 « encoded word », only when needed (and never a line break: no header injection). */
    private fun encoded(text: String): String {
        val clean = text.replace(Regex("[\\r\\n]+"), " ")
        return if (clean.all { it.code in 32..126 }) clean
        else "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8)) + "?="
    }

    private fun dotStuffed(text: String): String = text.split("\r\n").joinToString("\r\n") { if (it.startsWith(".")) ".$it" else it }

    /** One SMTP dialogue: commands, multi-line replies (« 250-… », then « 250 … »). */
    private class Io(socket: Socket) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val out: OutputStream = socket.getOutputStream()

        fun write(text: String) {
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        /** The lines of the reply (without their code); an error when its code is not one of [codes]. */
        fun expect(vararg codes: Int, sent: String? = null): List<String> {
            val lines = ArrayList<String>()
            var code: Int
            while (true) {
                val line = reader.readLine() ?: throw SmtpException("Connection closed by the server" + (sent?.let { " after $it" } ?: ""), false)
                code = line.take(3).toIntOrNull() ?: throw SmtpException("Unexpected reply: ${line.take(100)}", false)
                lines += line.drop(4)
                if (line.length < 4 || line[3] != '-') break
            }
            if (code !in codes) {
                throw SmtpException("${sent ?: "Connection"}: $code ${lines.lastOrNull().orEmpty()}".take(300), code in 500..599)
            }
            return lines
        }

        fun command(line: String, vararg codes: Int, secret: Boolean = false): List<String> {
            write("$line\r\n")
            return expect(*codes, sent = if (secret) line.substringBefore(' ').let { "$it …" } else line.substringBefore(':').take(40))
        }
    }

    /** Email addresses as SMTP accepts them, without anything that could add a header or a command. */
    object Addresses {
        private val ADDRESS = Regex("""^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$""")
        fun valid(address: String): Boolean = address.length <= 254 && ADDRESS.matches(address)
    }
}
