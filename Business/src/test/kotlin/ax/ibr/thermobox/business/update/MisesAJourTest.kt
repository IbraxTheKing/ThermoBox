package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artefact
import ax.ibr.thermobox.common.update.Composant
import ax.ibr.thermobox.common.update.EtatEtape
import ax.ibr.thermobox.common.update.EtatInstallation
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.NiveauControle
import ax.ibr.thermobox.common.update.OptionsInstallation
import ax.ibr.thermobox.common.update.TypeMiseAJour
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
class MisesAJourTest {

    /** Stands for the REST server or the JSF interface. */
    private class Faux(override val composant: Composant) : ComposantLocal {
        @Volatile var enService: String? = null
        val bascules = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun empechement(): String? = null
        override val effetBascule = "Redémarre"
        override fun versionDuDossier(dossier: DossierMisesAJour) = enService
        override fun conserverActuels(cible: Path) {
            Files.writeString(cible.resolve(if (composant == Composant.REST) Artefacts.REST else Artefacts.WAR), "ancien")
        }
        override fun basculer(dossier: Path, tag: String) {
            bascules += tag
        }
    }

    private class FauxGitHub : AutoCloseable {
        val fichiers = HashMap<String, ByteArray>()
        @Volatile var releases = "[]"
        @Volatile var statut = 200
        private val serveur = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/repos/ib/tb/releases") { ex ->
                val corps = releases.toByteArray()
                if (statut != 200) ex.responseHeaders.add("x-ratelimit-reset", "${Instant.now().epochSecond + 600}")
                ex.sendResponseHeaders(statut, corps.size.toLong())
                ex.responseBody.use { it.write(corps) }
            }
            // Like GitHub: the download URL redirects to the CDN.
            createContext("/dl/") { ex ->
                ex.responseHeaders.add("Location", ex.requestURI.path.replace("/dl/", "/cdn/"))
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
            createContext("/cdn/") { ex ->
                val f = fichiers[ex.requestURI.path.removePrefix("/cdn/")]
                if (f == null) {
                    ex.sendResponseHeaders(404, -1); ex.close()
                } else {
                    ex.sendResponseHeaders(200, f.size.toLong())
                    ex.responseBody.use { it.write(f) }
                }
            }
            start()
        }
        val url = "http://127.0.0.1:${serveur.address.port}"

        /** Publishes [tag] with its three files; [digests] can replace a published SHA-256. */
        fun publier(tag: String, java: Int = 21, prerelease: Boolean = false, notes: String = "", digests: Map<String, String> = emptyMap()): String {
            val contenus = mapOf(Artefacts.REST to restJar(), Artefacts.BASE to baseJar(java), Artefacts.WAR to war())
            contenus.forEach { (nom, octets) -> fichiers["$tag/$nom"] = octets }
            val assets = contenus.entries.joinToString(",") { (nom, octets) ->
                """{"name":"$nom","browser_download_url":"$url/dl/$tag/$nom","size":${octets.size},"digest":"sha256:${digests[nom] ?: sha(octets)}"}"""
            }
            return """{"tag_name":"$tag","name":"$tag","body":${json(notes)},"prerelease":$prerelease,"draft":false,
                "published_at":"2026-10-20T10:00:00Z","html_url":"https://github.com/ib/tb/releases/tag/$tag","assets":[$assets]}"""
        }

        override fun close() = serveur.stop(0)

        private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }

    private fun configuration(github: FauxGitHub, dossier: Path = Files.createTempDirectory("tb-maj")) = ConfigMisesAJour(
        depot = "ib/tb", verificationAutomatique = false, dossier = dossier, apiGitHub = github.url, httpLocalAutorise = true,
    )

    private fun service(config: ConfigMisesAJour, composant: ComposantLocal, installee: String = "26.10.1", java: Int = 23) =
        UpdateServiceImpl(
            config = config,
            sauvegarderBase = { f -> Files.writeString(f, "-- dump"); "2 tables, 10 lignes" },
            versionDuBuild = Version.parse(installee),
            javaDuServeur = java,
            delaiBascule = Duration.ZERO,
        ).apply { demarrer(composant, planifier = false) }

    private fun attendre(service: UpdateServiceImpl, condition: (Installation) -> Boolean): Installation {
        val fin = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < fin) {
            service.getEtat().installation?.takeIf(condition)?.let { return it }
            Thread.sleep(50)
        }
        error("Délai dépassé : ${service.getEtat().installation}")
    }

    private fun finie(i: Installation) = i.etat != EtatInstallation.EN_COURS

    @Test
    fun `corrective, verification, installation, bascule des deux composants`() = FauxGitHub().use { gh ->
        gh.releases = "[${gh.publier("26.12.0-beta.1", prerelease = true)},${gh.publier("26.11.0")},${gh.publier("26.10.2")},${gh.publier("26.10.0")}]"
        val config = configuration(gh)
        val rest = Faux(Composant.REST)
        val service = service(config, rest)

        val etat = service.verifier()
        assertNull(etat.erreur)
        assertEquals(listOf("26.11.0", "26.10.2"), etat.disponibles.map { it.version.tag })
        assertEquals("26.10.2", etat.corrective?.version?.tag)
        assertEquals("26.11.0", etat.fonctionnelle?.version?.tag)

        val prep = service.preparer("26.10.2")
        assertTrue(prep.installable)
        assertFalse(prep.confirmationRequise)
        assertFalse(prep.sauvegardeObligatoire)
        assertTrue(prep.retourSimple)

        service.installer("26.10.2", "admin", OptionsInstallation(sauvegarderBase = false))
        val i = attendre(service, ::finie)
        assertEquals(EtatInstallation.REDEMARRAGE, i.etat, i.message)
        assertEquals(EtatEtape.IGNOREE, i.etapes[0].etat)
        assertTrue(i.etapes.dropLast(1).drop(1).all { it.etat == EtatEtape.OK }, i.etapes.toString())
        Thread.sleep(200)
        assertEquals(listOf("26.10.2"), rest.bascules)
        assertEquals("26.10.2", service.dossier.courante())
        assertTrue(service.dossier.complete("26.10.2"))
        // The running version's files are kept for a manual rollback.
        assertEquals("ancien", Files.readString(config.dossier.resolve("updates/26.10.1/RestServer.jar")))

        // Restarted on the new version: the installation is over, 26.10.2 is no longer offered.
        rest.enService = "26.10.2"
        service.suivre()
        assertEquals(EtatInstallation.TERMINEE, service.getEtat().installation?.etat)
        assertEquals("26.10.2", service.getEtat().installee?.tag)
        assertEquals(listOf("26.11.0"), service.getEtat().disponibles.map { it.version.tag })

        // The JSF interface shares the folder: it switches by itself.
        val jsf = Faux(Composant.JSF)
        val autre = service(config, jsf)
        autre.suivre()
        Thread.sleep(300)
        assertEquals(listOf("26.10.2"), jsf.bascules)
        // And keeps its own previous files too.
        assertEquals("ancien", Files.readString(config.dossier.resolve("updates/26.10.1/JsfClient.war")))
    }

    @Test
    fun `fonctionnelle, avertissements acceptes et sauvegarde obligatoire`() = FauxGitHub().use { gh ->
        gh.releases = "[${gh.publier("26.11.0", notes = "## Migration\n- Ajouter la colonne photo")}]"
        val service = service(configuration(gh), Faux(Composant.REST))
        service.verifier()

        val prep = service.preparer("26.11.0")
        assertEquals(TypeMiseAJour.FONCTIONNELLE, prep.version.type)
        assertTrue(prep.confirmationRequise)
        assertTrue(prep.sauvegardeObligatoire)
        assertFalse(prep.retourSimple)
        assertTrue(prep.controles.any { it.niveau == NiveauControle.AVERTISSEMENT && it.detail.contains("colonne photo") })

        assertThrows(IllegalArgumentException::class.java) { service.installer("26.11.0", "admin", OptionsInstallation()) }

        // Backup asked off, but done anyway for a feature update.
        service.installer("26.11.0", "admin", OptionsInstallation(sauvegarderBase = false, risquesAcceptes = true))
        val i = attendre(service, ::finie)
        assertEquals(EtatInstallation.REDEMARRAGE, i.etat, i.message)
        assertEquals(EtatEtape.OK, i.etapes[0].etat)
        assertTrue(Files.exists(Path.of(i.sauvegarde!!)))
    }

    @Test
    fun `fichier altere, installation refusee, rien n'est mis en place`() = FauxGitHub().use { gh ->
        gh.releases = "[${gh.publier("26.10.2", digests = mapOf(Artefacts.BASE to "0".repeat(64)))}]"
        val rest = Faux(Composant.REST)
        val service = service(configuration(gh), rest)
        service.verifier()
        service.installer("26.10.2", "admin", OptionsInstallation(sauvegarderBase = false))
        val i = attendre(service, ::finie)
        assertEquals(EtatInstallation.ECHEC, i.etat)
        assertTrue(i.message.contains("SHA-256"), i.message)
        assertNull(service.dossier.courante())
        assertTrue(rest.bascules.isEmpty())
        assertFalse(Files.list(service.dossier.versions).use { l -> l.anyMatch { it.fileName.toString().startsWith(".telechargement") } })
    }

    @Test
    fun `version qui demande un Java plus recent`() = FauxGitHub().use { gh ->
        gh.releases = "[${gh.publier("26.10.2", java = 30)}]"
        val service = service(configuration(gh), Faux(Composant.REST), java = 23)
        service.verifier()
        service.installer("26.10.2", "admin", OptionsInstallation(sauvegarderBase = false))
        val i = attendre(service, ::finie)
        assertEquals(EtatInstallation.ECHEC, i.etat)
        assertTrue(i.message.contains("Java 30"), i.message)
    }

    @Test
    fun `erreurs de GitHub remontees`() = FauxGitHub().use { gh ->
        gh.statut = 403
        val etat = service(configuration(gh), Faux(Composant.REST)).verifier()
        assertNotNull(etat.erreur)
        assertTrue(etat.erreur!!.contains("Limite"), etat.erreur)
        assertNotNull(etat.derniereVerification)
    }

    @Test
    fun `telechargement seulement depuis GitHub en HTTPS`() = FauxGitHub().use { gh ->
        val t = Telechargeur(configuration(gh).copy(httpLocalAutorise = false))
        val cible = Files.createTempFile("tb", ".jar")
        for (url in listOf("${gh.url}/dl/x/base.jar", "https://evil.example/base.jar", "http://github.com/base.jar")) {
            val e = assertThrows(IOException::class.java) { t.telecharger(Artefact("base.jar", url, 1, "00"), cible) }
            assertTrue(e.message!!.contains("refusé"), e.message)
        }
        assertThrows(IOException::class.java) { t.telecharger(Artefact("base.jar", "https://github.com/x", 1, null), cible) }
    }

    @Test
    fun `installation interrompue ne bloque pas la suivante`() = FauxGitHub().use { gh ->
        gh.releases = "[${gh.publier("26.10.2")}]"
        val service = service(configuration(gh), Faux(Composant.REST))
        service.verifier()
        service.dossier.ecrireInstallation(InstallationJson.versJson(Installation(
            "x", "26.10.2", "26.10.1", TypeMiseAJour.CORRECTIVE, Composant.JSF, "admin", Instant.now(),
            EtatInstallation.EN_COURS, emptyList(),
        )))
        assertTrue(service.preparer("26.10.2").installable)
        assertEquals(EtatInstallation.ECHEC, service.getEtat().installation?.etat)
    }

    private companion object {
        fun sha(octets: ByteArray) = MessageDigest.getInstance("SHA-256").digest(octets).joinToString("") { "%02x".format(it) }

        fun restJar(): ByteArray {
            val manifeste = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = "ax.ibr.thermobox.restserver.MainKt"
                mainAttributes[Attributes.Name.CLASS_PATH] = "base.jar"
            }
            return ByteArrayOutputStream().also { b -> JarOutputStream(b, manifeste).use { } }.toByteArray()
        }

        /** A class header (magic + version) is all Artefacts.inspecter reads. */
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
