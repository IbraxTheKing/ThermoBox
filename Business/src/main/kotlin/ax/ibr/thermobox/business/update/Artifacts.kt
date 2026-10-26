package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipFile

/** The three deliverables of a release (deploy/), as they are named in the data folder. */
object Artifacts {
    const val REST = "RestServer.jar"
    const val BASE = "base.jar"
    const val WAR = "JsfClient.war"
    val ALL = listOf(REST, BASE, WAR)

    /** Class of the update system: a version without it can't be updated from the interface. */
    private const val UPDATE_CLASS = "ax/ibr/thermobox/business/update/UpdateServiceImpl.class"

    /**
     * Assets to download for a release: the three files when they are published separately
     * (nightly), otherwise the zip that holds them (stable releases). The WAR was once called
     * JsfServer.war: any single .war is taken.
     */
    fun choose(artifacts: List<Artifact>): List<Artifact>? {
        val byName = artifacts.associateBy { it.name }
        val war = byName[WAR] ?: artifacts.singleOrNull { it.name.endsWith(".war", ignoreCase = true) }
        val rest = byName[REST]
        val base = byName[BASE]
        if (rest != null && base != null && war != null) return listOf(rest, base, war)
        val zips = artifacts.filter { it.name.endsWith(".zip", ignoreCase = true) }
        return (zips.firstOrNull { it.name.startsWith("thermobox", ignoreCase = true) } ?: zips.singleOrNull())?.let { listOf(it) }
    }

    /** Name of a downloaded asset in the folder: the WAR is renamed, a zip keeps its name. */
    fun localName(artifact: Artifact): String =
        if (artifact.name.endsWith(".war", ignoreCase = true)) WAR else artifact.name

    /**
     * Copies RestServer.jar, base.jar and the WAR out of [zip] into [directory]. Only the file name
     * of each entry is used (never its path), so an entry can't be written outside [directory].
     */
    fun extract(zip: Path, directory: Path) {
        ZipFile(zip.toFile()).use { z ->
            val files = z.entries().asSequence().filter { !it.isDirectory }.toList()
            fun copy(entry: java.util.zip.ZipEntry, name: String) =
                z.getInputStream(entry).use { Files.copy(it, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
            fun name(e: java.util.zip.ZipEntry) = e.name.substringAfterLast('/')
            for (expected in listOf(REST, BASE)) {
                val e = files.firstOrNull { name(it) == expected } ?: throw IOException("$expected absent de ${zip.fileName}")
                copy(e, expected)
            }
            val wars = files.filter { name(it).endsWith(".war", ignoreCase = true) }
            val war = wars.firstOrNull { name(it) == WAR } ?: wars.singleOrNull() ?: throw IOException("WAR absent de ${zip.fileName}")
            copy(war, WAR)
        }
    }

    /** Result of [inspect]: what the downloaded files need and contain. */
    data class Content(val requiredJava: Int, val hasUpdater: Boolean)

    /** Checks that the three files are what they should be, and reads the Java version they need. */
    fun inspect(directory: Path): Content {
        JarFile(directory.resolve(REST).toFile()).use { jar ->
            val manifest = jar.manifest?.mainAttributes ?: throw IOException("$REST has no manifest")
            if (manifest.getValue("Main-Class").isNullOrBlank()) throw IOException("$REST is not executable (no Main-Class)")
            if (manifest.getValue("Class-Path")?.split(' ')?.contains(BASE) != true) throw IOException("$REST does not reference $BASE")
        }
        var requiredJava = 0
        var hasUpdater = false
        JarFile(directory.resolve(BASE).toFile()).use { jar ->
            val classes = jar.entries().asSequence().filter { it.name.endsWith(".class") && !it.name.contains("META-INF/") }.toList()
            if (classes.isEmpty()) throw IOException("$BASE contains no class")
            classes.take(50).forEach { e -> jar.getInputStream(e).use { requiredJava = maxOf(requiredJava, javaVersion(it)) } }
            hasUpdater = jar.getEntry(UPDATE_CLASS) != null
        }
        ZipFile(directory.resolve(WAR).toFile()).use { war ->
            if (war.getEntry("WEB-INF/web.xml") == null) throw IOException("$WAR is not a web application (no WEB-INF/web.xml)")
        }
        return Content(requiredJava, hasUpdater)
    }

    /** Java release a class file needs (major version 67 = Java 23). */
    fun javaVersion(classFile: InputStream): Int {
        val header = classFile.readNBytes(8)
        if (header.size < 8 || header[0] != 0xCA.toByte() || header[1] != 0xFE.toByte()) throw IOException("Invalid .class file")
        val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
        return major - 44
    }
}
