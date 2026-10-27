package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import ax.ibr.thermobox.common.update.Component
import ax.ibr.thermobox.common.update.StepState
import ax.ibr.thermobox.common.update.InstallationState
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.CheckLevel
import ax.ibr.thermobox.common.update.InstallOptions
import ax.ibr.thermobox.common.update.UpdateType
import ax.ibr.thermobox.common.update.Version
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Whole update flow against a fake GitHub (local HTTP server): check, prepare, install, switch. */
class UpdatesTest {

    /** Stands for a REST server. */
    private class Fake(override val component: Component) : LocalComponent {
        @Volatile var inService: String? = null
        val switches = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun blocker(): String? = null
        override val switchEffect = "Restarts"
        override fun versionInDirectory(directory: UpdateDirectory) = inService
        override fun keepCurrent(target: Path) {
            Files.writeString(target.resolve(Artifacts.REST), "old")
        }
        override fun switchTo(directory: Path, tag: String) {
            switches += tag
        }
    }

    private class FakeGitHub : AutoCloseable {
        val files = HashMap<String, ByteArray>()
        @Volatile var releases = "[]"
        @Volatile var status = 200
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/repos/ib/tb/releases") { ex ->
                val body = releases.toByteArray()
                if (status != 200) ex.responseHeaders.add("x-ratelimit-reset", "${Instant.now().epochSecond + 600}")
                ex.sendResponseHeaders(status, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            // Like GitHub: the download URL redirects to the CDN.
            createContext("/dl/") { ex ->
                ex.responseHeaders.add("Location", ex.requestURI.path.replace("/dl/", "/cdn/"))
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
            createContext("/cdn/") { ex ->
                val f = files[ex.requestURI.path.removePrefix("/cdn/")]
                if (f == null) {
                    ex.sendResponseHeaders(404, -1); ex.close()
                } else {
                    ex.sendResponseHeaders(200, f.size.toLong())
                    ex.responseBody.use { it.write(f) }
                }
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}"

        /** Publishes [tag] with its three files (the WAR is the "JSF removed" page, never downloaded); [digests] can replace a published SHA-256. */
        fun publish(tag: String, java: Int = 21, prerelease: Boolean = false, notes: String = "", digests: Map<String, String> = emptyMap()): String {
            val contents = mapOf(Artifacts.REST to restJar(), Artifacts.BASE to baseJar(java), "JsfClient.war" to war())
            contents.forEach { (name, bytes) -> files["$tag/$name"] = bytes }
            val assets = contents.entries.joinToString(",") { (name, bytes) ->
                """{"name":"$name","browser_download_url":"$url/dl/$tag/$name","size":${bytes.size},"digest":"sha256:${digests[name] ?: sha(bytes)}"}"""
            }
            return """{"tag_name":"$tag","name":"$tag","body":${json(notes)},"prerelease":$prerelease,"draft":false,
                "published_at":"2026-10-20T10:00:00Z","html_url":"https://github.com/ib/tb/releases/tag/$tag","assets":[$assets]}"""
        }

        override fun close() = server.stop(0)

        private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }

    private fun configuration(github: FakeGitHub, directory: Path = Files.createTempDirectory("tb-maj")) = UpdateConfig(
        repository = "ib/tb", automaticCheck = false, directory = directory, gitHubApi = github.url, allowLocalHttp = true,
    )

    private fun service(config: UpdateConfig, component: LocalComponent, installed: String = "26.10.1", java: Int = 23) =
        UpdateServiceImpl(
            config = config,
            backupDatabase = { f -> Files.writeString(f, "-- dump"); "2 tables, 10 rows" },
            buildVersion = Version.parse(installed),
            serverJava = java,
            switchDelay = Duration.ZERO,
        ).apply { start(component, schedule = false) }

    private fun await(service: UpdateServiceImpl, condition: (Installation) -> Boolean): Installation {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            service.getStatus().installation?.takeIf(condition)?.let { return it }
            Thread.sleep(50)
        }
        error("Timed out: ${service.getStatus().installation}")
    }

    private fun finished(i: Installation) = i.state != InstallationState.RUNNING

    @Test
    fun `patch, check, installation, switch of the servers sharing the folder`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.12.0-beta.1", prerelease = true)},${gh.publish("26.11.0")},${gh.publish("26.10.2")},${gh.publish("26.10.0")}]"
        val config = configuration(gh)
        val rest = Fake(Component.REST)
        val service = service(config, rest)

        val state = service.check()
        assertNull(state.error)
        assertEquals(listOf("26.11.0", "26.10.2"), state.available.map { it.version.tag })
        assertEquals("26.10.2", state.latestPatch?.version?.tag)
        assertEquals("26.11.0", state.latestFeature?.version?.tag)

        val prep = service.prepare("26.10.2")
        assertTrue(prep.installable)
        assertFalse(prep.confirmationRequired)
        assertFalse(prep.backupRequired)
        assertTrue(prep.simpleRollback)

        service.install("26.10.2", "admin", InstallOptions(backupDatabase = false))
        val i = await(service, ::finished)
        assertEquals(InstallationState.RESTARTING, i.state, i.message)
        assertEquals(StepState.SKIPPED, i.steps[0].state)
        assertTrue(i.steps.dropLast(1).drop(1).all { it.state == StepState.OK }, i.steps.toString())
        Thread.sleep(200)
        assertEquals(listOf("26.10.2"), rest.switches)
        assertEquals("26.10.2", service.directory.current())
        assertTrue(service.directory.complete("26.10.2"))
        // The running version's files are kept for a manual rollback.
        assertEquals("old", Files.readString(config.directory.resolve("updates/26.10.1/RestServer.jar")))

        // Restarted on the new version: the installation is over, 26.10.2 is no longer offered.
        rest.inService = "26.10.2"
        service.follow()
        assertEquals(InstallationState.DONE, service.getStatus().installation?.state)
        assertEquals("26.10.2", service.getStatus().installed?.tag)
        assertEquals(listOf("26.11.0"), service.getStatus().available.map { it.version.tag })

        // Another server sharing the folder, still on 26.10.1: it switches by itself.
        val second = Fake(Component.REST).apply { inService = "26.10.1" }
        val other = service(config, second)
        other.follow()
        Thread.sleep(300)
        assertEquals(listOf("26.10.2"), second.switches)
        // The WAR published for the old JSF interfaces is never downloaded.
        assertFalse(Files.exists(config.directory.resolve("updates/26.10.2/JsfClient.war")))
    }

    @Test
    fun `feature, warnings accepted and mandatory backup`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.11.0", notes = "## Migration\n- Add the photo column")}]"
        val service = service(configuration(gh), Fake(Component.REST))
        service.check()

        val prep = service.prepare("26.11.0")
        assertEquals(UpdateType.FEATURE, prep.version.type)
        assertTrue(prep.confirmationRequired)
        assertTrue(prep.backupRequired)
        assertFalse(prep.simpleRollback)
        assertTrue(prep.checks.any { it.level == CheckLevel.WARNING && it.detail.contains("photo column") })

        assertThrows(IllegalArgumentException::class.java) { service.install("26.11.0", "admin", InstallOptions()) }

        // Backup asked off, but done anyway for a feature update.
        service.install("26.11.0", "admin", InstallOptions(backupDatabase = false, risksAccepted = true))
        val i = await(service, ::finished)
        assertEquals(InstallationState.RESTARTING, i.state, i.message)
        assertEquals(StepState.OK, i.steps[0].state)
        assertTrue(Files.exists(Path.of(i.backup!!)))
    }

    @Test
    fun `tampered file, installation refused, nothing is put in place`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.10.2", digests = mapOf(Artifacts.BASE to "0".repeat(64)))}]"
        val rest = Fake(Component.REST)
        val service = service(configuration(gh), rest)
        service.check()
        service.install("26.10.2", "admin", InstallOptions(backupDatabase = false))
        val i = await(service, ::finished)
        assertEquals(InstallationState.FAILED, i.state)
        assertTrue(i.message.contains("SHA-256"), i.message)
        assertNull(service.directory.current())
        assertTrue(rest.switches.isEmpty())
        assertFalse(Files.list(service.directory.versions).use { l -> l.anyMatch { it.fileName.toString().startsWith(".download") } })
    }

    @Test
    fun `version that needs a newer Java`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.10.2", java = 30)}]"
        val service = service(configuration(gh), Fake(Component.REST), java = 23)
        service.check()
        service.install("26.10.2", "admin", InstallOptions(backupDatabase = false))
        val i = await(service, ::finished)
        assertEquals(InstallationState.FAILED, i.state)
        assertTrue(i.message.contains("Java 30"), i.message)
    }

    @Test
    fun `prerelease channel, older versions can be reinstalled`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.11.0-nightly.3", prerelease = true)},${gh.publish("26.11.0-nightly.2", prerelease = true)}," +
            "${gh.publish("26.10.1")},${gh.publish("26.10.0")}]"
        val config = configuration(gh).copy(channel = ax.ibr.thermobox.common.update.Channel.PRERELEASE)
        val rest = Fake(Component.REST)
        val service = UpdateServiceImpl(config, backupDatabase = { f -> Files.writeString(f, "-- dump"); "dump" },
            buildVersion = Version.parse("26.11.0-nightly.2"), serverJava = 23, switchDelay = Duration.ZERO)
            .apply { start(rest, schedule = false) }
        val state = service.check()
        assertEquals(listOf("26.11.0-nightly.3"), state.available.map { it.version.tag })
        assertEquals(listOf("26.10.1", "26.10.0"), state.olderVersions.map { it.version.tag })
        assertTrue(state.olderVersions.all { it.older })

        val prep = service.prepare("26.10.1")
        assertTrue(prep.backupRequired)
        assertTrue(prep.confirmationRequired)
        assertTrue(prep.checks.any { it.title.startsWith("Rollback to an older version") })

        service.install("26.10.1", "admin", InstallOptions(backupDatabase = false, risksAccepted = true))
        val i = await(service, ::finished)
        assertEquals(InstallationState.RESTARTING, i.state, i.message)
        assertEquals(StepState.OK, i.steps[0].state)
        Thread.sleep(200)
        assertEquals(listOf("26.10.1"), rest.switches)

        // Stable channel: no older versions offered.
        val stable = service(configuration(gh), Fake(Component.REST), installed = "26.10.1")
        assertTrue(stable.check().olderVersions.isEmpty())
    }

    @Test
    fun `no going back past a version whose notes say so`() = FakeGitHub().use { gh ->
        // 26.11.0 forbids going back; 26.11.1 (installed) allows going back to 26.11.0
        gh.releases = "[${gh.publish("26.11.1", notes = "Fixes.\nRétrocompatibilité : oui")}," +
            "${gh.publish("26.11.0", notes = "## Migration\n- V8\n\nRétrocompatibilité : non")}," +
            "${gh.publish("26.10.1")},${gh.publish("26.10.0")}]"
        val config = configuration(gh).copy(channel = ax.ibr.thermobox.common.update.Channel.PRERELEASE)
        val service = service(config, Fake(Component.REST), installed = "26.11.1")
        val state = service.check()

        assertEquals("Fixes.\nRétrocompatibilité : oui", state.installedNotes)
        assertEquals(true, state.installedRollback)
        val older = state.olderVersions.associateBy { it.version.tag }
        assertNull(older.getValue("26.11.0").blockedBy)
        assertFalse(older.getValue("26.11.0").incompatible)
        assertEquals("26.11.0", older.getValue("26.10.1").blockedBy?.tag)
        assertTrue(older.getValue("26.10.1").incompatible)
        assertTrue(older.getValue("26.10.0").incompatible)

        val prep = service.prepare("26.10.1")
        assertTrue(prep.checks.any { it.level == CheckLevel.WARNING && it.title == "No going back past 26.11.0" })
        assertTrue(prep.checks.none { it.title == "The notes announce an incompatibility" })

        // Even outside the older versions listed: the installed version counts too
        gh.releases = "[${gh.publish("26.11.1", notes = "Rollback: no")},${gh.publish("26.10.1")}]"
        val state2 = service.check()
        assertEquals(false, state2.installedRollback)
        assertEquals("26.11.1", state2.olderVersions.single().blockedBy?.tag)
    }

    @Test
    fun `GitHub errors are reported`() = FakeGitHub().use { gh ->
        gh.status = 403
        val state = service(configuration(gh), Fake(Component.REST)).check()
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("limit"), state.error)
        assertNotNull(state.lastCheck)
    }

    @Test
    fun `downloads only from GitHub over HTTPS`() = FakeGitHub().use { gh ->
        val t = Downloader(configuration(gh).copy(allowLocalHttp = false))
        val target = Files.createTempFile("tb", ".jar")
        for (url in listOf("${gh.url}/dl/x/base.jar", "https://evil.example/base.jar", "http://github.com/base.jar")) {
            val e = assertThrows(IOException::class.java) { t.download(Artifact("base.jar", url, 1, "00"), target) }
            assertTrue(e.message!!.contains("refused"), e.message)
        }
        assertThrows(IOException::class.java) { t.download(Artifact("base.jar", "https://github.com/x", 1, null), target) }
    }

    @Test
    fun `an interrupted installation does not block the next one`() = FakeGitHub().use { gh ->
        gh.releases = "[${gh.publish("26.10.2")}]"
        val service = service(configuration(gh), Fake(Component.REST))
        service.check()
        service.directory.writeInstallation(InstallationJson.toJson(Installation(
            "x", "26.10.2", "26.10.1", UpdateType.PATCH, Component.REST, "admin", Instant.now(),
            InstallationState.RUNNING, emptyList(),
        )))
        assertTrue(service.prepare("26.10.2").installable)
        assertEquals(InstallationState.FAILED, service.getStatus().installation?.state)
    }

    private companion object {
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun restJar(): ByteArray {
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = "ax.ibr.thermobox.restserver.MainKt"
                mainAttributes[Attributes.Name.CLASS_PATH] = "base.jar"
            }
            return ByteArrayOutputStream().also { b -> JarOutputStream(b, manifest).use { } }.toByteArray()
        }

        /** A class header (magic + version) is all Artifacts.inspect reads. */
        fun baseJar(java: Int): ByteArray = ByteArrayOutputStream().also { b ->
            JarOutputStream(b).use { j ->
                j.putNextEntry(ZipEntry("ax/ibr/A.class"))
                j.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, (java + 44).toByte()))
                j.closeEntry()
                j.putNextEntry(ZipEntry("ax/ibr/thermobox/business/update/UpdateServiceImpl.class"))
                j.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 65))
                j.closeEntry()
            }
        }.toByteArray()

        fun war(): ByteArray = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("WEB-INF/web.xml"))
                z.write("<web-app/>".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
    }
}
