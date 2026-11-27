package ax.ibr.thermobox.restserver.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread

class TlsKeysTest {

    private fun pem(type: String, der: ByteArray) =
        "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END $type-----\n"

    @Test
    fun `a self-signed certificate is made once, for the names asked, and kept`() {
        val dir = Files.createTempDirectory("tb-tls")
        val config = TlsConfig(dataDirectory = dir, extraNames = listOf("thermobox.lan", "192.168.1.20"))
        val first = TlsKeys.load(config)
        assertTrue(first.selfSigned)
        val names = SelfSignedCertificate.namesOf(first.certificate)
        assertTrue(names.containsAll(listOf("localhost", "127.0.0.1", "thermobox.lan", "192.168.1.20")), names.toString())
        assertEquals(first.fingerprint, TlsKeys.load(config).fingerprint)
        // A name more: another certificate, with the names it had
        val second = TlsKeys.load(config.copy(extraNames = listOf("autre.lan")))
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertTrue(SelfSignedCertificate.namesOf(second.certificate).containsAll(listOf("thermobox.lan", "autre.lan")))
        // 30 days before its end: made again
        val later = TlsKeys.selfSigned(dir.resolve("tls"), setOf("localhost"), Instant.now().plusSeconds(820L * 86_400))
        assertNotEquals(second.fingerprint, later.fingerprint)
    }

    @Test
    fun `the self-signed certificate serves TLS for its names`() {
        val keys = TlsKeys.selfSigned(Files.createTempDirectory("tb-tls"), setOf("localhost", "thermobox.lan"))
        val server = SSLContext.getInstance("TLS").apply {
            init(KeyManagerFactory.getInstance("PKIX").apply { init(keys.keyStore, keys.password.toCharArray()) }.keyManagers, null, null)
        }.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val accepted = thread { server.accept().use { it.getOutputStream().write(42); it.getOutputStream().flush() } }
        val trust = java.security.KeyStore.getInstance("PKCS12").apply { load(null, null); setCertificateEntry("tb", keys.certificate) }
        val client = SSLContext.getInstance("TLS").apply {
            init(null, TrustManagerFactory.getInstance("PKIX").apply { init(trust) }.trustManagers, null)
        }.socketFactory.createSocket("127.0.0.1", server.localPort) as SSLSocket
        client.sslParameters = client.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(javax.net.ssl.SNIHostName("thermobox.lan")) }
        client.use { assertEquals(42, it.getInputStream().read()) }
        accepted.join(2000)
        server.close()
    }

    @Test
    fun `PEM certificates and keys, PKCS#8 and RSA`() {
        val dir = Files.createTempDirectory("tb-tls")
        val made = TlsKeys.selfSigned(dir, setOf("localhost"))
        val cert = dir.resolve("cert.pem").also { Files.writeString(it, pem("CERTIFICATE", made.certificate.encoded)) }
        val ecKey = made.keyStore.getKey("thermobox", made.password.toCharArray())
        val key = dir.resolve("key.pem").also { Files.writeString(it, pem("PRIVATE KEY", ecKey.encoded)) }
        val loaded = TlsKeys.pem(cert, key)
        assertEquals(made.fingerprint, loaded.fingerprint)
        assertEquals(listOf(cert, key), loaded.watched)

        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals("RSA", TlsKeys.privateKey(pem("PRIVATE KEY", rsa.private.encoded)).algorithm)
        assertThrows(IllegalArgumentException::class.java) { TlsKeys.privateKey(pem("ENCRYPTED PRIVATE KEY", byteArrayOf(1))) }
        assertThrows(IllegalArgumentException::class.java) { TlsKeys.privateKey("not a key") }
    }

    @Test
    fun `the configuration refuses what it cannot use`() {
        assertEquals(TlsConfig().copy(dataDirectory = Path.of("/d")), TlsConfig.fromEnvironment(mapOf("THERMOBOX_DATA_DIR" to "/d")))
        assertThrows(IllegalStateException::class.java) { TlsConfig.fromEnvironment(mapOf("THERMOBOX_TLS_CERT" to "c.pem")) }
        assertThrows(IllegalStateException::class.java) { TlsConfig.fromEnvironment(mapOf("THERMOBOX_HTTPS" to "maybe")) }
        assertThrows(IllegalStateException::class.java) { TlsConfig.fromEnvironment(mapOf("THERMOBOX_HTTPS_PORT" to "8080")) }
        assertThrows(IllegalStateException::class.java) { TlsConfig.fromEnvironment(mapOf("THERMOBOX_HTTPS" to "false", "THERMOBOX_HTTP_PORT" to "0")) }
        val c = TlsConfig.fromEnvironment(mapOf("THERMOBOX_HTTP_PORT" to "0", "THERMOBOX_TLS_HOSTNAMES" to "a.lan, b.lan", "THERMOBOX_DATA_DIR" to "/d"))
        assertEquals(0, c.httpPort)
        assertEquals(listOf("a.lan", "b.lan"), c.extraNames)
    }
}
