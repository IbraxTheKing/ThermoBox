package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Artifact
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * The deliverables of a release (deploy/) the REST server installs, as named in the data folder.
 *
 * Releases also publish a `JsfClient.war`, which the REST server never downloads: since 26.12 it is
 * only a page saying the JSF interface was removed, for the JSF interfaces of 26.11 and before that
 * update themselves (their update system needs a WAR).
 */
object Artifacts {
    const val REST = "RestServer.jar"
    const val BASE = "base.jar"
    val ALL = listOf(REST, BASE)

    /** Class of the update system: a version without it can't be updated from the interface. */
    private const val UPDATE_CLASS = "ax/ibr/thermobox/business/update/UpdateServiceImpl.class"

    /**
     * Assets to download for a release: RestServer.jar and base.jar when they are published
     * separately, otherwise the zip that holds them.
     */
    fun choose(artifacts: List<Artifact>): List<Artifact>? {
        val byName = artifacts.associateBy { it.name }
        val rest = byName[REST]
        val base = byName[BASE]
        if (rest != null && base != null) return listOf(rest, base)
        val zips = artifacts.filter { it.name.endsWith(".zip", ignoreCase = true) }
        return (zips.firstOrNull { it.name.startsWith("thermobox", ignoreCase = true) } ?: zips.singleOrNull())?.let { listOf(it) }
    }

    /** Name of a downloaded asset in the folder. */
    fun localName(artifact: Artifact): String = artifact.name

    /**
     * Copies RestServer.jar and base.jar out of [zip] into [directory]. Only the file name
     * of each entry is used (never its path), so an entry can't be written outside [directory].
     */
    fun extract(zip: Path, directory: Path) {
        ZipFile(zip.toFile()).use { z ->
            val files = z.entries().asSequence().filter { !it.isDirectory }.toList()
            fun copy(entry: java.util.zip.ZipEntry, name: String) =
                z.getInputStream(entry).use { Files.copy(it, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
            fun name(e: java.util.zip.ZipEntry) = e.name.substringAfterLast('/')
            for (expected in ALL) {
                val e = files.firstOrNull { name(it) == expected } ?: throw IOException("$expected missing from ${zip.fileName}")
                copy(e, expected)
            }
        }
    }

    /** Result of [inspect]: what the downloaded files need and contain. */
    data class Content(val requiredJava: Int, val hasUpdater: Boolean)

    /** Checks that the files are what they should be, and reads the Java version they need. */
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
