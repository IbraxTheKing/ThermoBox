package ax.ibr.thermobox.restserver.tls

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * The key and the certificate of the HTTPS server, as a keystore for Jetty:
 * 1. a PEM certificate and its key (THERMOBOX_TLS_CERT, THERMOBOX_TLS_KEY), as Let's Encrypt gives
 *    them (fullchain.pem, privkey.pem);
 * 2. or a PKCS#12 keystore (THERMOBOX_TLS_KEYSTORE);
 * 3. otherwise a self-signed certificate, made once and kept in `tls/` of the data folder (made
 *    again when it is about to expire, or when the server is reached under a name it lacks).
 */
object TlsKeys {

    /** The keystore, its password, what it is (for the log) and the files to watch for a renewal. */
    class Loaded(val keyStore: KeyStore, val password: String, val certificate: X509Certificate, val source: String, val watched: List<Path>) {
        /** SHA-256 of the certificate, as browsers show it. */
        val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
        val selfSigned: Boolean get() = source == SELF_SIGNED
    }

    const val SELF_SIGNED = "self-signed"
    private const val ALIAS = "thermobox"

    fun load(config: TlsConfig): Loaded = when {
        config.certFile != null && config.keyFile != null -> pem(config.certFile, config.keyFile)
        config.keystore != null -> pkcs12(config.keystore, config.keystorePassword.orEmpty())
        else -> selfSigned(config.dataDirectory.resolve("tls"), defaultNames(config))
    }

    // ------------------------------------------------------------------ PEM

    fun pem(certFile: Path, keyFile: Path): Loaded {
        val chain = CertificateFactory.getInstance("X.509").generateCertificates(Files.newInputStream(certFile).buffered())
            .map { it as X509Certificate }
        require(chain.isNotEmpty()) { "$certFile: no certificate (PEM « BEGIN CERTIFICATE »)" }
        val key = privateKey(Files.readString(keyFile), keyFile)
        val password = randomPassword()
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, key, password.toCharArray(), chain.toTypedArray())
        }
        return Loaded(ks, password, chain.first(), "$certFile", listOf(certFile, keyFile))
    }

    /** A private key in PEM: PKCS#8 (« BEGIN PRIVATE KEY », RSA or EC) or PKCS#1 (« BEGIN RSA PRIVATE KEY »). */
    fun privateKey(pem: String, file: Path? = null): PrivateKey {
        val where = file?.let { "$it: " } ?: ""
        require("ENCRYPTED" !in pem) { "${where}an encrypted key: give it without a password (openssl pkey -in key.pem -out key-clear.pem)" }
        val block = Regex("-----BEGIN ([A-Z ]+)-----([\\s\\S]+?)-----END \\1-----").find(pem)
            ?: throw IllegalArgumentException("${where}no private key (PEM « BEGIN PRIVATE KEY »)")
        val der = Base64.getMimeDecoder().decode(block.groupValues[2].trim())
        return when (block.groupValues[1]) {
            "PRIVATE KEY" -> {
                val spec = PKCS8EncodedKeySpec(der)
                listOf("RSA", "EC", "Ed25519").firstNotNullOfOrNull { runCatching { KeyFactory.getInstance(it).generatePrivate(spec) }.getOrNull() }
                    ?: throw IllegalArgumentException("${where}an unknown kind of key (RSA or EC expected)")
            }
            // PKCS#1: wrapped in PKCS#8 (algorithm rsaEncryption)
            "RSA PRIVATE KEY" -> KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs1ToPkcs8(der)))
            "EC PRIVATE KEY" -> throw IllegalArgumentException("${where}an EC key in SEC1: convert it to PKCS#8 (openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem)")
            else -> throw IllegalArgumentException("${where}« ${block.groupValues[1]} » is not a private key")
        }
    }

    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
        val algorithm = byteArrayOf(0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00)
        val version = byteArrayOf(0x02, 0x01, 0x00)
        return der(0x30, version + algorithm + der(0x04, pkcs1))
    }

    private fun der(tag: Int, value: ByteArray): ByteArray {
        val n = value.size
        val length = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + value
    }

    // ------------------------------------------------------------------ PKCS#12

    fun pkcs12(file: Path, password: String): Loaded {
        val ks = KeyStore.getInstance("PKCS12")
        Files.newInputStream(file).use { ks.load(it, password.toCharArray()) }
        val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) } ?: throw IllegalArgumentException("$file: no private key in the keystore")
        // Only the entry of the key: Jetty picks the first key it finds
        val out = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, ks.getKey(alias, password.toCharArray()), password.toCharArray(), ks.getCertificateChain(alias))
        }
        return Loaded(out, password, ks.getCertificate(alias) as X509Certificate, "$file", listOf(file))
    }

    // ------------------------------------------------------------------ self-signed

    /**
     * localhost, the name of the machine, the address listened on and THERMOBOX_TLS_HOSTNAMES. Not
     * the addresses of the network cards, nor the name of a Docker container (a hexadecimal id):
     * they change from one start to the next, and every change would make another certificate,
     * one more for the clients to accept.
     */
    fun defaultNames(config: TlsConfig): Set<String> {
        val names = linkedSetOf("localhost", "127.0.0.1", "::1")
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?.takeIf { it.isNotBlank() && !CONTAINER_ID.matches(it) }?.let { names += it }
        if (config.host != "0.0.0.0" && config.host != "::") names += config.host
        names += config.extraNames
        return names.map { SelfSignedCertificate.normalize(it) }.toCollection(linkedSetOf())
    }

    private val CONTAINER_ID = Regex("^[0-9a-f]{12,64}$")

    /**
     * The self-signed certificate of [folder], made again when it is missing, unreadable, expiring
     * within 30 days or lacking one of [names] (it then keeps the names it had).
     */
    fun selfSigned(folder: Path, names: Set<String>, now: Instant = Instant.now()): Loaded {
        val file = folder.resolve("self-signed.p12")
        val passwordFile = folder.resolve("self-signed.password")
        val current = runCatching {
            val password = Files.readString(passwordFile).trim()
            pkcs12(file, password)
        }.getOrNull()
        val certificate = current?.certificate
        val kept = certificate?.let { SelfSignedCertificate.namesOf(it) }.orEmpty()
        val fresh = certificate != null && certificate.notAfter.toInstant().isAfter(now.plusSeconds(30L * 86_400)) && kept.containsAll(names)
        if (current != null && fresh) return Loaded(current.keyStore, current.password, current.certificate, SELF_SIGNED, emptyList())

        val generated = SelfSignedCertificate.generate(kept + names, now = now)
        val password = randomPassword()
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, generated.keyPair.private, password.toCharArray(), arrayOf(generated.certificate))
        }
        Files.createDirectories(folder)
        writePrivate(passwordFile, password.toByteArray())
        val bytes = java.io.ByteArrayOutputStream().also { ks.store(it, password.toCharArray()) }.toByteArray()
        writePrivate(file, bytes)
        return Loaded(ks, password, generated.certificate, SELF_SIGNED, emptyList())
    }

    /** Written aside then moved, readable by the owner only (where the system knows of it). */
    private fun writePrivate(file: Path, bytes: ByteArray) {
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.write(tmp, bytes)
        runCatching { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")) }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun randomPassword(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) })
}
