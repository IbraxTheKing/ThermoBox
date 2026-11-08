package ax.ibr.thermobox.business.audit

import ax.ibr.thermobox.common.audit.AuditEntry
import ax.ibr.thermobox.common.audit.AuditQuery
import ax.ibr.thermobox.persistence.dataservices.AuditDataService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The log of the changes made through the API (ThermoBox 26.12): every creation, change and
 * deletion, whoever made it (ADMIN or MANAGER), for the administrators (Serveur › Journal).
 *
 * - [record] writes aside, on a thread of its own: a request never waits for the log, and a
 *   database that refuses it never fails the change it tells.
 * - The entries are kept [retentionDays] days (90 by default, an ADMIN chooses, or for good),
 *   then removed by a daily purge ([start]). Kept in `audit.properties` of the data folder.
 */
class AuditLog(
    private val store: () -> AuditDataService,
    private val settingsFile: Path,
    private val clock: () -> LocalDateTime = { LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) },
) {
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "thermobox-audit").apply { isDaemon = true } }
    private var purger: ScheduledExecutorService? = null

    /** Writes [entry] (its date: now, when it has none) without making the caller wait. */
    fun record(entry: AuditEntry) {
        writer.execute {
            try {
                store().add(entry)
            } catch (e: Exception) {
                System.err.println("ThermoBox: change not logged (${entry.action} by ${entry.username}): ${e.message}")
            }
        }
    }

    /** Now, for the entries recorded. */
    fun now(): LocalDateTime = clock()

    fun find(query: AuditQuery): List<AuditEntry> = store().find(query)

    fun count(query: AuditQuery): Long = store().count(query)

    fun users(): List<Pair<Long?, String>> = store().users()

    // ------------------------------------------------------------------ retention

    /** Days the entries are kept; null: for good. */
    fun retentionDays(): Int? {
        val raw = read().getProperty(KEY)?.trim() ?: return DEFAULT_DAYS
        if (raw == FOREVER) return null
        return raw.toIntOrNull()?.takeIf { it in MIN_DAYS..MAX_DAYS } ?: DEFAULT_DAYS
    }

    /**
     * Keeps the entries [days] days (null: for good), then removes what is older at once.
     * @throws IllegalArgumentException outside [MIN_DAYS]..[MAX_DAYS]
     */
    @Synchronized
    fun setRetention(days: Int?): Int? {
        require(days == null || days in MIN_DAYS..MAX_DAYS) { "retentionDays: from $MIN_DAYS to $MAX_DAYS days, or null to keep them for good" }
        val p = read()
        p.setProperty(KEY, days?.toString() ?: FOREVER)
        Files.createDirectories(settingsFile.toAbsolutePath().parent)
        // Written aside then moved: a reader never sees half a file
        val tmp = settingsFile.resolveSibling(settingsFile.fileName.toString() + ".tmp")
        Files.newBufferedWriter(tmp).use { p.store(it, "ThermoBox: how long the log of the changes is kept") }
        Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        runCatching { purge() }
        return days
    }

    /** Removes the entries older than the retention; answers how many. */
    fun purge(): Int {
        val days = retentionDays() ?: return 0
        return store().purge(clock().minusDays(days.toLong()))
    }

    /** The daily purge, a minute after the start then every day. */
    @Synchronized
    fun start() {
        if (purger != null) return
        purger = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "thermobox-audit-purge").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({
                try {
                    purge()
                } catch (e: Exception) {
                    System.err.println("ThermoBox: purge of the log failed: ${e.message}")
                }
            }, 1, 24 * 60, TimeUnit.MINUTES)
        }
    }

    @Synchronized
    fun stop() {
        purger?.shutdownNow()
        purger = null
        writer.shutdown()
        writer.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun read(): Properties = Properties().apply {
        if (Files.exists(settingsFile)) runCatching { Files.newBufferedReader(settingsFile).use { load(it) } }
    }

    companion object {
        const val DEFAULT_DAYS = 90
        const val MIN_DAYS = 1
        const val MAX_DAYS = 3650
        private const val KEY = "retentionDays"
        private const val FOREVER = "forever"
    }
}
