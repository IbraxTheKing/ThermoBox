package ax.ibr.thermobox.business.alerts

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption of the secrets of the alert channels (SMTP password, webhook URL and token) before
 * they go to the database: a dump of the database, or a backup made before an update, does not
 * give them away.
 *
 * AES-256-GCM, a random nonce per value: « v1: » + Base64(nonce + ciphertext + tag). The key comes
 * from THERMOBOX_SECRETS_KEY, otherwise from `secrets.key` in the data folder, created (readable
 * by the server's user only) the first time a secret is saved. Servers that share the database
 * share the data folder (or the variable), so they read each other's secrets.
 */
class AlertSecrets(private val keyFile: Path, private val configuredKey: String? = null) {

    @Volatile private var key: ByteArray? = null

    fun encrypt(plain: String): String {
        val k = key(create = true)!!
        val nonce = ByteArray(NONCE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(nonce + sealed)
    }

    /** @throws IllegalStateException when the value cannot be read: other key, damaged value */
    fun decrypt(text: String): String {
        require(text.startsWith(PREFIX)) { "Not an encrypted value" }
        val k = key(create = false) ?: throw IllegalStateException(
            "The key of the secrets is missing ($keyFile, or THERMOBOX_SECRETS_KEY): enter the passwords and URLs of the channels again")
        val bytes = try {
            Base64.getDecoder().decode(text.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Damaged secret")
        }
        if (bytes.size <= NONCE) throw IllegalStateException("Damaged secret")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BITS, bytes, 0, NONCE))
            String(cipher.doFinal(bytes, NONCE, bytes.size - NONCE), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("A secret cannot be read with the current key (secrets.key or THERMOBOX_SECRETS_KEY changed): enter it again")
        }
    }

    @Synchronized
    private fun key(create: Boolean): ByteArray? {
        key?.let { return it }
        val k = configuredKey?.let { parse(it, "THERMOBOX_SECRETS_KEY") }
            ?: if (Files.exists(keyFile)) parse(Files.readString(keyFile).trim(), keyFile.toString())
            else if (create) generate() else null
        key = k
        return k
    }

    private fun parse(text: String, origin: String): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(text) }.getOrNull()
        check(bytes != null && bytes.size == 32) { "$origin must hold 32 bytes in Base64 (openssl rand -base64 32)" }
        return bytes
    }

    private fun generate(): ByteArray {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        Files.createDirectories(keyFile.toAbsolutePath().parent)
        val tmp = keyFile.resolveSibling("${keyFile.fileName}.tmp")
        Files.writeString(tmp, Base64.getEncoder().encodeToString(bytes) + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        runCatching { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")) }
        try {
            // Another server sharing the folder may have created it meanwhile: its key wins
            Files.move(tmp, keyFile)
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            Files.deleteIfExists(tmp)
            return parse(Files.readString(keyFile).trim(), keyFile.toString())
        }
        return bytes
    }

    companion object {
        private const val PREFIX = "v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE = 12
        private const val TAG_BITS = 128
        private val random = SecureRandom()

        const val KEY_FILE = "secrets.key"
    }
}
