package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artefact
import ax.ibr.thermobox.persistence.backup.SauvegardeBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NotesEtArtefactsTest {

    @Test
    fun `section Migration des notes`() {
        val notes = """
            ## Nouveautés
            - Mises à jour depuis l'interface

            ## Migration
            - Ajouter la colonne `salle.photo`
            - Relancer HashExistingPasswords

            ## Corrections
            - Bug du curseur
        """.trimIndent()
        assertEquals("- Ajouter la colonne `salle.photo`\n- Relancer HashExistingPasswords", NotesDeVersion.migration(notes))
        assertEquals("- vider le cache", NotesDeVersion.migration("Correctifs.\nMigration : vider le cache"))
        assertNull(NotesDeVersion.migration("## What's Changed\n* Fix by @ib"))
    }

    @Test
    fun `incompatibilite annoncee`() {
        assertTrue(NotesDeVersion.incompatible("Cette version est incompatible avec la base précédente"))
        assertTrue(NotesDeVersion.incompatible("BREAKING CHANGE: table renamed"))
        assertFalse(NotesDeVersion.incompatible("Compatible avec 26.10.x"))
    }

    @Test
    fun `releases GitHub`() {
        val json = """[{"tag_name":"26.10.2","name":"","body":null,"prerelease":false,"draft":false,
            "published_at":"2026-10-20T10:00:00Z","html_url":"https://github.com/x/y/releases/tag/26.10.2",
            "assets":[{"name":"base.jar","browser_download_url":"https://github.com/x/y/releases/download/26.10.2/base.jar",
            "size":12,"digest":"sha256:ABCDEF"},{"name":"old.jar","browser_download_url":"u","size":1,"digest":null}]}]"""
        val r = GitHubReleases.analyser(json).single()
        assertEquals("26.10.2", r.titre)
        assertEquals("", r.notes)
        assertEquals("abcdef", r.artefacts[0].sha256)
        assertNull(r.artefacts[1].sha256)
    }

    @Test
    fun `choix des fichiers a telecharger`() {
        fun a(n: String) = Artefact(n, "https://github.com/$n", 1, "00")
        assertEquals(listOf("RestServer.jar", "base.jar", "JsfClient.war"),
            Artefacts.choisir(listOf(a("thermobox-26.10.2.zip"), a("JsfClient.war"), a("base.jar"), a("RestServer.jar")))!!.map { it.nom })
        assertEquals(listOf("thermobox-server-26.10.1.zip"), Artefacts.choisir(listOf(a("thermobox-server-26.10.1.zip")))!!.map { it.nom })
        assertEquals("JsfClient.war", Artefacts.nomLocal(a("JsfServer.war")))
        assertNull(Artefacts.choisir(listOf(a("notes.txt"))))
    }

    @Test
    fun `extraction du zip sans sortir du dossier`() {
        val dossier = Files.createTempDirectory("tb-zip")
        val zip = dossier.resolve("t.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { z ->
            for ((nom, contenu) in listOf("deploy/RestServer.jar" to "r", "../../base.jar" to "b", "deploy/JsfServer.war" to "w")) {
                z.putNextEntry(ZipEntry(nom)); z.write(contenu.toByteArray()); z.closeEntry()
            }
        }
        val sortie = Files.createDirectories(dossier.resolve("out"))
        Artefacts.extraire(zip, sortie)
        assertEquals("b", Files.readString(sortie.resolve("base.jar")))
        assertEquals("w", Files.readString(sortie.resolve("JsfClient.war")))
        assertFalse(Files.exists(dossier.parent.resolve("base.jar")) && Files.readString(dossier.parent.resolve("base.jar")) == "b")
    }

    @Test
    fun `version de Java d'un fichier class`() {
        val entete = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 67)
        assertEquals(23, Artefacts.versionJava(ByteArrayInputStream(entete)))
    }

    @Test
    fun `litteraux SQL de la sauvegarde`() {
        assertEquals("'l\\'été\\\\x\\n'", SauvegardeBase.texte("l'été\\x\n"))
        assertEquals("NULL", SauvegardeBase.texte(null))
        assertEquals("X'00ff10'", SauvegardeBase.octets(byteArrayOf(0, -1, 16)))
        assertEquals("''", SauvegardeBase.octets(ByteArray(0)))
        assertEquals("`a``b`", SauvegardeBase.identifiant("a`b"))
    }
}
