package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import ax.ibr.thermobox.persistence.backup.DatabaseBackup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReleaseNotesAndArtifactsTest {

    @Test
    fun `Migration section of the notes`() {
        val notes = """
            ## What's new
            - Updates from the interface

            ## Migration
            - Add the column `room.photo`
            - Run HashExistingPasswords again

            ## Fixes
            - Cursor bug
        """.trimIndent()
        assertEquals("- Add the column `room.photo`\n- Run HashExistingPasswords again", ReleaseNotes.migration(notes))
        assertEquals("- clear the cache", ReleaseNotes.migration("Fixes.\nMigration: clear the cache"))
        assertNull(ReleaseNotes.migration("## What's Changed\n* Fix by @ib"))
    }

    @Test
    fun `announced incompatibility`() {
        assertTrue(ReleaseNotes.incompatible("This version is incompatible with the previous database"))
        assertTrue(ReleaseNotes.incompatible("BREAKING CHANGE: table renamed"))
        assertFalse(ReleaseNotes.incompatible("Compatible with 26.10.x"))
    }

    @Test
    fun `GitHub releases`() {
        val json = """[{"tag_name":"26.10.2","name":"","body":null,"prerelease":false,"draft":false,
            "published_at":"2026-10-20T10:00:00Z","html_url":"https://github.com/x/y/releases/tag/26.10.2",
            "assets":[{"name":"base.jar","browser_download_url":"https://github.com/x/y/releases/download/26.10.2/base.jar",
            "size":12,"digest":"sha256:ABCDEF"},{"name":"old.jar","browser_download_url":"u","size":1,"digest":null}]}]"""
        val r = GitHubReleases.analyze(json).single()
        assertEquals("26.10.2", r.title)
        assertEquals("", r.notes)
        assertEquals("abcdef", r.artifacts[0].sha256)
        assertNull(r.artifacts[1].sha256)
    }

    @Test
    fun `choice of the files to download`() {
        fun a(n: String) = Artifact(n, "https://github.com/$n", 1, "00")
        assertEquals(listOf("RestServer.jar", "base.jar", "JsfClient.war"),
            Artifacts.choose(listOf(a("thermobox-26.10.2.zip"), a("JsfClient.war"), a("base.jar"), a("RestServer.jar")))!!.map { it.name })
        assertEquals(listOf("thermobox-server-26.10.1.zip"), Artifacts.choose(listOf(a("thermobox-server-26.10.1.zip")))!!.map { it.name })
        assertEquals("JsfClient.war", Artifacts.localName(a("JsfServer.war")))
        assertNull(Artifacts.choose(listOf(a("notes.txt"))))
    }

    @Test
    fun `zip extraction never leaves the directory`() {
        val directory = Files.createTempDirectory("tb-zip")
        val zip = directory.resolve("t.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { z ->
            for ((name, content) in listOf("deploy/RestServer.jar" to "r", "../../base.jar" to "b", "deploy/JsfServer.war" to "w")) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry()
            }
        }
        val output = Files.createDirectories(directory.resolve("out"))
        Artifacts.extract(zip, output)
        assertEquals("b", Files.readString(output.resolve("base.jar")))
        assertEquals("w", Files.readString(output.resolve("JsfClient.war")))
        assertFalse(Files.exists(directory.parent.resolve("base.jar")) && Files.readString(directory.parent.resolve("base.jar")) == "b")
    }

    @Test
    fun `Java version of a class file`() {
        val header = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 67)
        assertEquals(23, Artifacts.javaVersion(ByteArrayInputStream(header)))
    }

    @Test
    fun `SQL literals of the backup`() {
        assertEquals("'it\\'s\\\\x\\n'", DatabaseBackup.text("it's\\x\n"))
        assertEquals("NULL", DatabaseBackup.text(null))
        assertEquals("X'00ff10'", DatabaseBackup.bytes(byteArrayOf(0, -1, 16)))
        assertEquals("''", DatabaseBackup.bytes(ByteArray(0)))
        assertEquals("`a``b`", DatabaseBackup.identifier("a`b"))
    }
}
