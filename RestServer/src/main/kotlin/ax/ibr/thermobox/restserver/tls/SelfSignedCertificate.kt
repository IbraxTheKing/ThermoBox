package ax.ibr.thermobox.restserver.tls

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A self-signed certificate for the server when none is given: an EC P-256 key, « CN=ThermoBox »,
 * valid [days] days, for the names and addresses [names] (subjectAltName: DNS names, IP addresses).
 *
 * Written with the JDK only (X.509 in DER, signed with SHA256withECDSA): no library for it.
 */
object SelfSignedCertificate {

    class Generated(val keyPair: KeyPair, val certificate: X509Certificate)

    fun generate(names: Collection<String>, days: Long = 825, now: Instant = Instant.now()): Generated {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()
        val algorithm = seq(oid(ECDSA_SHA256))
        val name = seq(set(seq(oid(COMMON_NAME), utf8("ThermoBox"))))
        val serial = BigInteger(127, SecureRandom()).add(BigInteger.ONE)
        val tbs = seq(
            explicit(0, integer(BigInteger.TWO)),
            integer(serial),
            algorithm,
            name,
            seq(time(now.minusSeconds(3600)), time(now.plusSeconds(days * 86_400))),
            name,
            keys.public.encoded,
            explicit(3, seq(
                extension(BASIC_CONSTRAINTS, true, seq()),
                // digitalSignature
                extension(KEY_USAGE, true, bitString(byteArrayOf(0x80.toByte()), unused = 7)),
                extension(EXTENDED_KEY_USAGE, false, seq(oid(SERVER_AUTH))),
                extension(SUBJECT_ALT_NAME, false, seq(*names.distinct().map { generalName(it) }.toTypedArray())),
            )),
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(tbs)
            sign()
        }
        val der = seq(tbs, algorithm, bitString(signature))
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
        certificate.verify(keys.public)
        return Generated(keys, certificate)
    }

    /** The DNS names and IP addresses a certificate is valid for. */
    fun namesOf(certificate: X509Certificate): Set<String> =
        certificate.subjectAlternativeNames.orEmpty().mapNotNull { it.getOrNull(1) as? String }.map { normalize(it) }.toSet()

    /** An IP address in one spelling (« ::1 » and « 0:0:0:0:0:0:0:1 » are the same). */
    fun normalize(name: String): String =
        if (isIp(name)) InetAddress.getByName(name).hostAddress.lowercase() else name.lowercase()

    fun isIp(name: String): Boolean = IPV4.matches(name) || (':' in name && name.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' })

    // ------------------------------------------------------------------ DER

    private const val ECDSA_SHA256 = "1.2.840.10045.4.3.2"
    private const val COMMON_NAME = "2.5.4.3"
    private const val BASIC_CONSTRAINTS = "2.5.29.19"
    private const val KEY_USAGE = "2.5.29.15"
    private const val EXTENDED_KEY_USAGE = "2.5.29.37"
    private const val SUBJECT_ALT_NAME = "2.5.29.17"
    private const val SERVER_AUTH = "1.3.6.1.5.5.7.3.1"
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    private fun generalName(name: String): ByteArray =
        if (isIp(name)) tlv(0x87, InetAddress.getByName(name).address) // [7] iPAddress
        else tlv(0x82, name.toByteArray(Charsets.US_ASCII)) // [2] dNSName

    private fun extension(id: String, critical: Boolean, value: ByteArray): ByteArray =
        if (critical) seq(oid(id), tlv(0x01, byteArrayOf(0xFF.toByte())), tlv(0x04, value)) else seq(oid(id), tlv(0x04, value))

    private fun time(at: Instant): ByteArray {
        val utc = at.atOffset(ZoneOffset.UTC)
        return if (utc.year < 2050) tlv(0x17, UTC.format(utc).toByteArray()) else tlv(0x18, GENERALIZED.format(utc).toByteArray())
    }

    private val UTC = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")
    private val GENERALIZED = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")

    private fun seq(vararg parts: ByteArray) = tlv(0x30, concat(parts))
    private fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))
    private fun explicit(tag: Int, inner: ByteArray) = tlv(0xA0 + tag, inner)
    private fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun bitString(bits: ByteArray, unused: Int = 0) = tlv(0x03, byteArrayOf(unused.toByte()) + bits)

    private fun oid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (p in parts.drop(2)) {
            val bytes = ArrayList<Int>()
            var v = p
            bytes += (v and 0x7F).toInt()
            v = v shr 7
            while (v > 0) {
                bytes += ((v and 0x7F) or 0x80).toInt()
                v = v shr 7
            }
            bytes.reversed().forEach { out.write(it) }
        }
        return tlv(0x06, out.toByteArray())
    }

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = value.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            n < 0x10000 -> { out.write(0x82); out.write(n shr 8); out.write(n and 0xFF) }
            else -> { out.write(0x83); out.write(n shr 16); out.write((n shr 8) and 0xFF); out.write(n and 0xFF) }
        }
        out.write(value)
        return out.toByteArray()
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
}
