package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artefact
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipFile

/** The three deliverables of a release (deploy/), as they are named in the data folder. */
object Artefacts {
    const val REST = "RestServer.jar"
    const val BASE = "base.jar"
    const val WAR = "JsfClient.war"
    val TOUS = listOf(REST, BASE, WAR)

    /** Class of the update system: a version without it can't be updated from the interface. */
    private const val CLASSE_MISE_A_JOUR = "ax/ibr/thermobox/business/update/UpdateServiceImpl.class"

    /**
     * Assets to download for a release: the three files when they are published separately
     * (nightly), otherwise the zip that holds them (stable releases). The WAR was once called
     * JsfServer.war: any single .war is taken.
     */
    fun choisir(artefacts: List<Artefact>): List<Artefact>? {
        val parNom = artefacts.associateBy { it.nom }
        val war = parNom[WAR] ?: artefacts.singleOrNull { it.nom.endsWith(".war", ignoreCase = true) }
        val rest = parNom[REST]
        val base = parNom[BASE]
        if (rest != null && base != null && war != null) return listOf(rest, base, war)
        val zips = artefacts.filter { it.nom.endsWith(".zip", ignoreCase = true) }
        return (zips.firstOrNull { it.nom.startsWith("thermobox", ignoreCase = true) } ?: zips.singleOrNull())?.let { listOf(it) }
    }

    /** Name of a downloaded asset in the folder: the WAR is renamed, a zip keeps its name. */
    fun nomLocal(artefact: Artefact): String =
        if (artefact.nom.endsWith(".war", ignoreCase = true)) WAR else artefact.nom

    /**
     * Copies RestServer.jar, base.jar and the WAR out of [zip] into [dossier]. Only the file name
     * of each entry is used (never its path), so an entry can't be written outside [dossier].
     */
    fun extraire(zip: Path, dossier: Path) {
        ZipFile(zip.toFile()).use { z ->
            val fichiers = z.entries().asSequence().filter { !it.isDirectory }.toList()
            fun copier(entree: java.util.zip.ZipEntry, nom: String) =
                z.getInputStream(entree).use { Files.copy(it, dossier.resolve(nom), StandardCopyOption.REPLACE_EXISTING) }
            fun nom(e: java.util.zip.ZipEntry) = e.name.substringAfterLast('/')
            for (attendu in listOf(REST, BASE)) {
                val e = fichiers.firstOrNull { nom(it) == attendu } ?: throw IOException("$attendu absent de ${zip.fileName}")
                copier(e, attendu)
            }
            val wars = fichiers.filter { nom(it).endsWith(".war", ignoreCase = true) }
            val war = wars.firstOrNull { nom(it) == WAR } ?: wars.singleOrNull() ?: throw IOException("WAR absent de ${zip.fileName}")
            copier(war, WAR)
        }
    }

    /** Result of [inspecter]: what the downloaded files need and contain. */
    data class Contenu(val javaRequis: Int, val miseAJourIntegree: Boolean)

    /** Checks that the three files are what they should be, and reads the Java version they need. */
    fun inspecter(dossier: Path): Contenu {
        JarFile(dossier.resolve(REST).toFile()).use { jar ->
            val manifeste = jar.manifest?.mainAttributes ?: throw IOException("$REST sans manifeste")
            if (manifeste.getValue("Main-Class").isNullOrBlank()) throw IOException("$REST n'est pas exécutable (Main-Class absente)")
            if (manifeste.getValue("Class-Path")?.split(' ')?.contains(BASE) != true) throw IOException("$REST ne référence pas $BASE")
        }
        var javaRequis = 0
        var integree = false
        JarFile(dossier.resolve(BASE).toFile()).use { jar ->
            val classes = jar.entries().asSequence().filter { it.name.endsWith(".class") && !it.name.contains("META-INF/") }.toList()
            if (classes.isEmpty()) throw IOException("$BASE ne contient aucune classe")
            classes.take(50).forEach { e -> jar.getInputStream(e).use { javaRequis = maxOf(javaRequis, versionJava(it)) } }
            integree = jar.getEntry(CLASSE_MISE_A_JOUR) != null
        }
        ZipFile(dossier.resolve(WAR).toFile()).use { war ->
            if (war.getEntry("WEB-INF/web.xml") == null) throw IOException("$WAR n'est pas une application web (WEB-INF/web.xml absent)")
        }
        return Contenu(javaRequis, integree)
    }

    /** Java release a class file needs (major version 67 = Java 23). */
    fun versionJava(classe: InputStream): Int {
        val entete = classe.readNBytes(8)
        if (entete.size < 8 || entete[0] != 0xCA.toByte() || entete[1] != 0xFE.toByte()) throw IOException("Fichier .class invalide")
        val majeure = ((entete[6].toInt() and 0xFF) shl 8) or (entete[7].toInt() and 0xFF)
        return majeure - 44
    }
}
