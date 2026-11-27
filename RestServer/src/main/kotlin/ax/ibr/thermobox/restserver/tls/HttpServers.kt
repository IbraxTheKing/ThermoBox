package ax.ibr.thermobox.restserver.tls

import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.ForwardedRequestCustomizer
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.SecuredRedirectHandler
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.glassfish.jersey.jetty.JettyHttpContainer
import org.glassfish.jersey.jetty.JettyHttpContainerFactory
import org.glassfish.jersey.server.ContainerFactory
import org.glassfish.jersey.server.ResourceConfig
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The Jetty server of the API: HTTPS on [TlsConfig.httpsPort], and on [TlsConfig.httpPort] a
 * redirect to it (308: the method and the body are kept, an old client that calls the HTTP port
 * follows it), or the API itself when [TlsConfig.redirect] is false. THERMOBOX_HTTPS=false: HTTP
 * only, as before.
 *
 * Behind a reverse proxy that ends TLS and calls the HTTP port, the proxy says « X-Forwarded-Proto:
 * https »: the request counts as secure, no redirect (no loop).
 *
 * A certificate given as files (PEM, PKCS#12) is read again once an hour when it changed: a
 * renewal (Let's Encrypt every 60 to 90 days) is taken without a restart.
 */
object HttpServers {

    class Started(val server: Server, val urls: List<String>, val keys: TlsKeys.Loaded?, private val stopWatching: () -> Unit) {
        fun stop() {
            stopWatching()
            server.stop()
        }
    }

    fun start(config: ResourceConfig, tls: TlsConfig): Started {
        val container = ContainerFactory.createContainer(JettyHttpContainer::class.java, config)
        if (!tls.https) {
            val server = JettyHttpContainerFactory.createServer(URI.create("http://${uriHost(tls.host)}:${tls.httpPort}/"), null, container, false)
            server.connectors = arrayOf<Connector>(httpConnector(server, tls, securePort = null))
            server.start()
            return Started(server, listOf(url("http", tls.host, tls.httpPort)), null) {}
        }

        val keys = TlsKeys.load(tls)
        val ssl = SslContextFactory.Server().apply {
            keyStore = keys.keyStore
            keyStorePassword = keys.password
            setKeyManagerPassword(keys.password)
            // Old protocols off (TLS 1.2 and 1.3 only)
            setIncludeProtocols("TLSv1.3", "TLSv1.2")
        }
        val server = JettyHttpContainerFactory.createServer(URI.create("https://${uriHost(tls.host)}:${tls.httpsPort}/"), ssl, container, false)

        val secure = HttpConfiguration().apply {
            secureScheme = "https"
            securePort = tls.httpsPort
            sendServerVersion = false
            // The server is reached under names its certificate may not have (an address of the
            // local network with a self-signed certificate): not refused for it
            addCustomizer(SecureRequestCustomizer().apply { isSniHostCheck = false })
        }
        val https = ServerConnector(server, SslConnectionFactory(ssl, "http/1.1"), HttpConnectionFactory(secure)).apply {
            host = tls.host
            port = tls.httpsPort
        }
        val connectors = mutableListOf<Connector>(https)
        if (tls.httpPort != 0) connectors += httpConnector(server, tls, securePort = tls.httpsPort)
        server.connectors = connectors.toTypedArray()
        if (tls.httpPort != 0 && tls.redirect) {
            val api = server.handler
            server.handler = SecuredRedirectHandler(api, 308)
        }
        server.start()

        val urls = buildList {
            add(url("https", tls.host, tls.httpsPort))
            if (tls.httpPort != 0) add(url("http", tls.host, tls.httpPort) + if (tls.redirect) " (→ HTTPS)" else "")
        }
        return Started(server, urls, keys, watch(keys, tls, ssl))
    }

    private fun httpConnector(server: Server, tls: TlsConfig, securePort: Int?): ServerConnector {
        val http = HttpConfiguration().apply {
            sendServerVersion = false
            if (securePort != null) {
                secureScheme = "https"
                this.securePort = securePort
            }
            // X-Forwarded-Proto / Forwarded of a reverse proxy in front
            addCustomizer(ForwardedRequestCustomizer())
        }
        return ServerConnector(server, HttpConnectionFactory(http)).apply {
            host = tls.host
            port = tls.httpPort
        }
    }

    /** Reads the certificate files again when they change (checked hourly). */
    private fun watch(keys: TlsKeys.Loaded, tls: TlsConfig, ssl: SslContextFactory.Server): () -> Unit {
        if (keys.watched.isEmpty()) return {}
        var stamps = stamps(keys.watched)
        val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "thermobox-tls-reload").apply { isDaemon = true } }
        timer.scheduleWithFixedDelay({
            val now = stamps(keys.watched)
            if (now != stamps) {
                try {
                    val fresh = TlsKeys.load(tls)
                    ssl.reload { f ->
                        f.keyStore = fresh.keyStore
                        f.keyStorePassword = fresh.password
                        f.setKeyManagerPassword(fresh.password)
                    }
                    stamps = now
                    println("ThermoBox: HTTPS certificate reloaded (${fresh.source}, until ${fresh.certificate.notAfter})")
                } catch (e: Exception) {
                    // A renewal half written: tried again at the next check
                    System.err.println("ThermoBox: the new HTTPS certificate could not be read, the current one stays: ${e.message}")
                }
            }
        }, 1, 1, TimeUnit.HOURS)
        return { timer.shutdownNow() }
    }

    private fun stamps(files: List<Path>) = files.map { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }

    private fun uriHost(host: String) = if (':' in host && !host.startsWith('[')) "[$host]" else host

    private fun url(scheme: String, host: String, port: Int) = "$scheme://${uriHost(host)}:$port/"
}
