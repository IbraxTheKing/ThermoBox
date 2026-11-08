package ax.ibr.thermobox.restserver.tls

import java.nio.file.Path

/**
 * How the REST server listens (environment variables):
 *
 * | Variable | Default | |
 * | --- | --- | --- |
 * | `THERMOBOX_HTTPS` | `true` | `false`: HTTP only, as before 26.12 |
 * | `THERMOBOX_HTTPS_PORT` | `8443` | the API in HTTPS |
 * | `THERMOBOX_HTTP_PORT` | `8080` | HTTP: redirects to HTTPS (308); `0`: no HTTP port |
 * | `THERMOBOX_HTTP_REDIRECT` | `true` | `false`: HTTP serves the API too (a trusted network, a proxy that only speaks HTTP) |
 * | `THERMOBOX_HTTP_HOST` | `localhost` | the address listened on, both ports (`0.0.0.0`: every one) |
 * | `THERMOBOX_TLS_CERT` + `THERMOBOX_TLS_KEY` | | the certificate (PEM, its chain after it) and its private key (PEM, PKCS#8 or RSA) |
 * | `THERMOBOX_TLS_KEYSTORE` + `THERMOBOX_TLS_KEYSTORE_PASSWORD` | | or a PKCS#12 keystore (.p12 / .pfx) |
 * | `THERMOBOX_TLS_HOSTNAMES` | | names and addresses added to the self-signed certificate (comma separated) |
 *
 * Without a certificate given, a self-signed one is made and kept in `tls/` of the data folder.
 */
data class TlsConfig(
    val https: Boolean = true,
    val host: String = "localhost",
    val httpsPort: Int = 8443,
    /** 0: no HTTP port. */
    val httpPort: Int = 8080,
    val redirect: Boolean = true,
    val certFile: Path? = null,
    val keyFile: Path? = null,
    val keystore: Path? = null,
    val keystorePassword: String? = null,
    val extraNames: List<String> = emptyList(),
    val dataDirectory: Path = Path.of(System.getProperty("user.home"), ".thermobox"),
) {
    companion object {
        /** @throws IllegalStateException for a value that cannot be used, so a typo does not go unnoticed */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): TlsConfig {
            fun v(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun flag(name: String, default: Boolean) = when (val s = v(name)?.lowercase()) {
                null -> default
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> error("$name=$s is not valid: true or false")
            }
            fun port(name: String, default: Int, zero: Boolean = false): Int {
                val s = v(name) ?: return default
                val p = s.toIntOrNull()?.takeIf { it in (if (zero) 0 else 1)..65535 }
                return p ?: error("$name=$s is not valid: a port from ${if (zero) 0 else 1} to 65535")
            }
            val cert = v("THERMOBOX_TLS_CERT")?.let { Path.of(it) }
            val key = v("THERMOBOX_TLS_KEY")?.let { Path.of(it) }
            check((cert == null) == (key == null)) { "THERMOBOX_TLS_CERT and THERMOBOX_TLS_KEY go together" }
            val keystore = v("THERMOBOX_TLS_KEYSTORE")?.let { Path.of(it) }
            check(keystore == null || cert == null) { "THERMOBOX_TLS_KEYSTORE or THERMOBOX_TLS_CERT / THERMOBOX_TLS_KEY, not both" }
            val config = TlsConfig(
                https = flag("THERMOBOX_HTTPS", true),
                host = v("THERMOBOX_HTTP_HOST") ?: "localhost",
                httpsPort = port("THERMOBOX_HTTPS_PORT", 8443),
                httpPort = port("THERMOBOX_HTTP_PORT", 8080, zero = true),
                redirect = flag("THERMOBOX_HTTP_REDIRECT", true),
                certFile = cert,
                keyFile = key,
                keystore = keystore,
                keystorePassword = env["THERMOBOX_TLS_KEYSTORE_PASSWORD"],
                extraNames = v("THERMOBOX_TLS_HOSTNAMES")?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                dataDirectory = v("THERMOBOX_DATA_DIR")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".thermobox"),
            )
            check(!config.https || config.httpPort != config.httpsPort) { "THERMOBOX_HTTP_PORT and THERMOBOX_HTTPS_PORT must differ" }
            check(config.https || config.httpPort != 0) { "THERMOBOX_HTTPS=false needs THERMOBOX_HTTP_PORT" }
            return config
        }
    }
}
