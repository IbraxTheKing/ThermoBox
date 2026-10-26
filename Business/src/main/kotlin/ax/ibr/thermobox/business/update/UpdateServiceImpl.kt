package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Channel
import ax.ibr.thermobox.common.update.Component
import ax.ibr.thermobox.common.update.Check
import ax.ibr.thermobox.common.update.Step
import ax.ibr.thermobox.common.update.StepState
import ax.ibr.thermobox.common.update.InstallationState
import ax.ibr.thermobox.common.update.UpdateStatus
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.InstallMode
import ax.ibr.thermobox.common.update.CheckLevel
import ax.ibr.thermobox.common.update.InstallOptions
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.UpdateType
import ax.ibr.thermobox.common.update.UpdateService
import ax.ibr.thermobox.common.update.Version
import ax.ibr.thermobox.common.update.AvailableVersion
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub for new versions (once a day, or on demand) and installs one:
 * database backup, download checked by SHA-256, files put in the data folder, then the
 * server switches to them ([LocalComponent]). A server sharing the data folder (or this one
 * after a restart) notices the new version there and switches too, within a minute.
 */
class UpdateServiceImpl(
    private val config: UpdateConfig = UpdateConfig.fromEnvironment(),
    private val github: GitHubReleases = GitHubReleases(config),
    private val downloader: Downloader = Downloader(config),
    /** Writes a database dump to the file and describes it. */
    private val backupDatabase: (Path) -> String = { f ->
        PersistenceFactory.backupDatabase(f).let { "${it.tables} tables, ${it.lines} rows, ${mb(it.bytes)}" }
    },
    private val clock: Clock = Clock.systemUTC(),
    private val buildVersion: Version? = BuildInfo.version,
    private val serverJava: Int = Runtime.version().feature(),
    /** Lets the HTTP response that started the installation reach the browser before the switch. */
    private val switchDelay: Duration = Duration.ofSeconds(3),
) : UpdateService {

    val directory = UpdateDirectory(config.directory)

    @Volatile private var local: LocalComponent? = null
    @Volatile private var releases: List<GitHubRelease> = emptyList()
    @Volatile private var lastCheck: Instant? = null
    @Volatile private var error: String? = null
    @Volatile private var installation: Installation? = null
    @Volatile private var pending: String? = null
    private val running = AtomicBoolean(false)
    private val switching = AtomicBoolean(false)
    private var scheduler: ScheduledExecutorService? = null

    /** Called once by the program at startup: daily check, and follows the shared folder. */
    @Synchronized
    fun start(component: LocalComponent, schedule: Boolean = true) {
        local = component
        if (scheduler != null || !schedule) return
        val p = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "thermobox-updates").apply { isDaemon = true } }
        if (config.automaticCheck) {
            p.scheduleAtFixedRate({ check() }, 1, config.checkInterval.toMinutes(), TimeUnit.MINUTES)
        }
        p.scheduleWithFixedDelay({ runCatching { follow() }.onFailure { log("follow-up: ${it.message}") } }, 5, 60, TimeUnit.SECONDS)
        scheduler = p
    }

    /** At shutdown, so the scheduled threads stop. */
    @Synchronized
    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    // ------------------------------------------------------------------ state

    private fun installed(): Version? =
        local?.let { runCatching { it.versionInDirectory(directory) }.getOrNull() }?.let { Version.parse(it) } ?: buildVersion

    override fun getStatus(): UpdateStatus {
        val installed = installed()
        val shared = directory.readInstallation()?.let { InstallationJson.fromJson(it) }
        return UpdateStatus(
            component = local?.component,
            installed = installed,
            channel = config.channel,
            automaticCheck = config.automaticCheck,
            lastCheck = lastCheck,
            error = error,
            available = available(installed),
            olderVersions = olderVersions(installed),
            mode = mode(),
            // The other program may have installed something since: the shared file wins.
            installation = listOfNotNull(installation, shared).maxByOrNull { it.startedAt },
            pending = pending,
        )
    }

    @Synchronized
    override fun check(): UpdateStatus {
        try {
            releases = github.list()
            error = null
            val state = getStatus()
            if (state.updateAvailable) {
                log("new version available: ${state.available.joinToString { "${it.version} (${it.type.label.lowercase()})" }}")
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            log("check failed: $error")
        }
        lastCheck = clock.instant()
        return getStatus()
    }

    private fun available(installed: Version?): List<AvailableVersion> {
        val prereleases = config.channel == Channel.PRERELEASE || installed?.isPrerelease == true
        val list = releases.asSequence()
            .filter { !it.draft }
            .mapNotNull { r -> Version.parse(r.tag)?.let { r to it } }
            .filter { (r, v) -> prereleases || (!r.prerelease && !v.isPrerelease) }
            .filter { (_, v) -> installed == null || v > installed }
            .sortedByDescending { it.second }
            .map { (r, v) ->
                AvailableVersion(
                    version = v, type = v.typeFrom(installed), title = r.title, notes = r.notes,
                    publishedAt = r.publishedAt, page = r.page, artifacts = r.artifacts,
                    migration = ReleaseNotes.migration(r.notes), incompatible = ReleaseNotes.incompatible(r.notes),
                )
            }
            .toList()
        // A development build is older than everything: the ten most recent are enough.
        return if (installed == null) list.take(10) else list
    }

    /**
     * Prerelease channel: the versions older than the installed one stay installable, to go
     * back after testing a nightly. Same series: patch; older series: feature (risky).
     */
    private fun olderVersions(installed: Version?): List<AvailableVersion> {
        if (config.channel != Channel.PRERELEASE || installed == null) return emptyList()
        return releases.asSequence()
            .filter { !it.draft }
            .mapNotNull { r -> Version.parse(r.tag)?.let { r to it } }
            .filter { (_, v) -> v < installed }
            .sortedByDescending { it.second }
            .take(MAX_OLDER)
            .map { (r, v) ->
                AvailableVersion(
                    version = v, type = v.typeFrom(installed), title = r.title, notes = r.notes,
                    publishedAt = r.publishedAt, page = r.page, artifacts = r.artifacts,
                    migration = ReleaseNotes.migration(r.notes), incompatible = ReleaseNotes.incompatible(r.notes),
                    older = true,
                )
            }
            .toList()
    }

    private fun mode(): InstallMode {
        val component = local ?: return InstallMode(false, "The update system is not started on this server.")
        if (!directory.writable()) {
            return InstallMode(false, "The data folder ${config.directory} is not writable (THERMOBOX_DATA_DIR variable).")
        }
        return component.blocker()?.let { InstallMode(false, it) } ?: InstallMode(true, component.switchEffect)
    }

    // ------------------------------------------------------------------ preparation

    override fun prepare(tag: String): Preparation {
        val installed = installed()
        val candidate = (available(installed) + olderVersions(installed)).firstOrNull { it.version.tag == tag || it.version == Version.parse(tag) }
            ?: throw IllegalArgumentException("Version $tag is unknown, or not newer than the installed version")
        val v = candidate.version
        val checks = ArrayList<Check>()

        if (candidate.older) {
            checks += Check(CheckLevel.WARNING, "Rollback to an older version: $installed → $v",
                "$installed may have changed the database: $v may not understand it. If it does not start " +
                    "or misbehaves, restore the backup made before $installed was installed.")
        } else if (candidate.type == UpdateType.PATCH) {
            checks += Check(CheckLevel.OK, "Patch update, recommended: ${installed} → $v",
                "Bug and security fixes of the ${v.series} series, with no new feature: the database " +
                    "is not changed, and going back to $installed stays possible.")
        } else {
            checks += Check(CheckLevel.WARNING, "Feature update: ${installed?.series ?: "unknown version"} → ${v.series}",
                "A new series may change the database schema or migrate data. Going back to " +
                    "${installed?.let { "${it.series}.x" } ?: "the current version"} is not guaranteed: it may be impossible " +
                    "without restoring the database backup made before the installation.")
            checks += Check(CheckLevel.INFO, "Database schema",
                "On the first start of $v, its schema migrations apply automatically (Flyway). " +
                    "Going back to an older version afterwards stays possible as long as these migrations only " +
                    "added tables or columns; otherwise, restore the backup.")
        }
        if (installed == null) {
            checks += Check(CheckLevel.WARNING, "Unknown installed version",
                "This server is a development build (${BuildInfo.text}): compatibility cannot be checked.")
        }
        if (v.isPrerelease) {
            checks += Check(CheckLevel.WARNING, "Test version",
                "$v is a pre-release (${v.prerelease}): it is not considered stable.")
        }
        if (installed != null && v.seriesRank - installed.seriesRank > 1) {
            val skipped = releases.mapNotNull { Version.parse(it.tag) }
                .filter { !it.isPrerelease && it.patch == 0 && it.seriesRank > installed.seriesRank && it.seriesRank < v.seriesRank }
                .sorted().joinToString()
            checks += Check(CheckLevel.WARNING, "Skipped monthly series",
                "From ${installed.series} to ${v.series}: the notes of " + (if (skipped.isEmpty()) "the series in between" else skipped) +
                    " apply too, read them.")
        }
        candidate.migration?.let { checks += Check(CheckLevel.WARNING, "Operations given by the release notes", it) }
        if (candidate.incompatible) {
            checks += Check(CheckLevel.WARNING, "The notes announce an incompatibility",
                "Read them before installing. To go back, the database backup will have to be restored.")
        }

        val files = Artifacts.choose(candidate.artifacts)
        val size = files?.sumOf { it.size } ?: 0L
        when {
            files == null -> checks += Check(CheckLevel.BLOCKING, "Deployment files missing",
                "The version publishes neither RestServer.jar and base.jar, nor a .zip archive that contains them.")
            files.any { it.sha256 == null } -> checks += Check(CheckLevel.BLOCKING, "SHA-256 digest not published",
                "Without a digest, the integrity of the files cannot be guaranteed: installation refused.")
            else -> checks += Check(CheckLevel.OK, "Files: ${files.joinToString { it.name }} (${mb(size)})",
                "Their SHA-256 digest, published by GitHub, is checked on download.")
        }
        checks += Check(CheckLevel.INFO, "Server Java: $serverJava",
            "The Java version the files need is checked after the download: the installation stops if it is newer.")

        val free = runCatching { directory.prepare(); Files.getFileStore(directory.versions).usableSpace }.getOrNull()
        if (free != null && free < 3 * size + DISK_MARGIN) {
            checks += Check(CheckLevel.BLOCKING, "Not enough disk space",
                "${mb(free)} free in ${config.directory}, ${mb(3 * size + DISK_MARGIN)} needed.")
        }

        val mode = mode()
        if (!mode.automatic) {
            checks += Check(CheckLevel.BLOCKING, "Automatic installation impossible on this server", mode.detail)
        } else {
            checks += Check(CheckLevel.INFO, "Restart", mode.detail)
        }
        if (installationActive()) {
            checks += Check(CheckLevel.BLOCKING, "An installation is already running")
        }

        val mandatory = candidate.type == UpdateType.FEATURE || candidate.older || candidate.migration != null ||
            candidate.incompatible || installed == null
        checks += Check(CheckLevel.INFO, "Database backup",
            (if (mandatory) "Always made before the installation" else "Optional (a patch does not change the database), made before the installation if ticked") +
                ", in ${directory.backups}. Restore: gunzip -c <file> | mysql -u <user> -p <database>.")

        return Preparation(
            version = candidate,
            installed = installed,
            checks = checks,
            backupRequired = mandatory,
            simpleRollback = !mandatory,
            confirmationRequired = checks.any { it.level == CheckLevel.WARNING },
        )
    }

    /**
     * An installation runs here, or in another server sharing the folder (it holds the lock). A "running" one
     * left in the shared file by a program that stopped meanwhile is marked as interrupted.
     */
    private fun installationActive(): Boolean {
        if (running.get()) return true
        val i = directory.readInstallation()?.let { InstallationJson.fromJson(it) } ?: return false
        if (i.state != InstallationState.RUNNING) return false
        val lock = runCatching { directory.lock() }.getOrNull() ?: return true
        lock.use {
            publish(i.copy(state = InstallationState.FAILED, message = "Installation interrupted (server stopped during the operation)",
                finishedAt = clock.instant()))
        }
        return false
    }

    // ------------------------------------------------------------------ installation

    override fun install(tag: String, user: String, options: InstallOptions): Installation {
        val preparation = prepare(tag)
        preparation.checks.firstOrNull { it.level == CheckLevel.BLOCKING }?.let {
            throw IllegalStateException(listOf(it.title, it.detail).filter { d -> d.isNotBlank() }.joinToString(" : "))
        }
        if (preparation.confirmationRequired && !options.risksAccepted) {
            throw IllegalArgumentException("Confirm you read the warnings before installing ${preparation.version.version}")
        }
        val component = local ?: throw IllegalStateException("The update system is not started")
        if (!running.compareAndSet(false, true)) throw IllegalStateException("An installation is already running")
        val lock = try {
            directory.lock()
        } catch (e: Exception) {
            running.set(false)
            throw IllegalStateException("Data folder unreachable: ${e.message}")
        }
        if (lock == null) {
            running.set(false)
            throw IllegalStateException("An installation is already running from another server sharing the data folder")
        }

        val withBackup = preparation.backupRequired || options.backupDatabase
        val initial = Installation(
            id = UUID.randomUUID().toString(),
            target = preparation.version.version.tag,
            from = preparation.installed?.tag,
            type = preparation.version.type,
            component = component.component,
            user = user,
            startedAt = clock.instant(),
            state = InstallationState.RUNNING,
            steps = STEPS.map { Step(it) } + Step("Restart: ${component.component.label}"),
        )
        publish(initial)
        log("installation of ${initial.target} started by $user")
        Thread({
            try {
                execute(preparation, component, withBackup)
            } finally {
                lock.close()
                running.set(false)
            }
        }, "thermobox-installation").apply { isDaemon = true }.start()
        return initial
    }

    private fun execute(preparation: Preparation, component: LocalComponent, withBackup: Boolean) {
        val candidate = preparation.version
        val target = candidate.version.tag
        val temporary = directory.versions.resolve(".download-$target")
        try {
            step(0) {
                if (!withBackup) return@step "Not requested" to StepState.SKIPPED
                Files.createDirectories(directory.backups)
                val file = directory.backups.resolve(
                    "thermobox-${preparation.installed?.tag ?: BuildInfo.text}-before-$target-${TIMESTAMP.format(clock.instant())}.sql.gz")
                val detail = backupDatabase(file)
                installation = installation?.copy(backup = file.toString())
                "$detail : $file" to StepState.OK
            }
            step(1) {
                delete(temporary)
                Files.createDirectories(temporary)
                val files = Artifacts.choose(candidate.artifacts) ?: error("Deployment files missing")
                for (a in files) {
                    var shown = 0L
                    downloader.download(a, temporary.resolve(Artifacts.localName(a))) { received ->
                        if (received - shown >= 2 * 1024 * 1024) {
                            shown = received
                            detail(1, "${a.name}: ${mb(received)} / ${mb(a.size)}")
                        }
                    }
                    if (a.name.endsWith(".zip", ignoreCase = true)) {
                        val zip = temporary.resolve(Artifacts.localName(a))
                        Artifacts.extract(zip, temporary)
                        Files.delete(zip)
                    }
                }
                "${files.joinToString { it.name }} (${mb(files.sumOf { it.size })}), SHA-256 digests match" to StepState.OK
            }
            step(2) {
                val content = Artifacts.inspect(temporary)
                if (content.requiredJava > serverJava) {
                    error("$target needs Java ${content.requiredJava}, this server runs Java $serverJava")
                }
                "Java ${content.requiredJava} required (server: Java $serverJava)" +
                    (if (content.hasUpdater) "" else "; this version does not include the update system: the next ones will have to be installed by hand") to StepState.OK
            }
            step(3) {
                val from = preparation.installed?.tag
                    ?: return@step "Unknown installed version: nothing to keep" to StepState.SKIPPED
                keep(component, from)?.let { "Not kept: $it" to StepState.SKIPPED }
                    ?: ("Current files (${component.component.label} $from) kept in ${directory.directory(from)}, for a manual rollback" to StepState.OK)
            }
            step(4) {
                directory.seal(temporary, target)
                val final = directory.directory(target)
                delete(final)
                Files.move(temporary, final, StandardCopyOption.ATOMIC_MOVE)
                directory.setCurrent(target)
                "Current version: $target ($final)" to StepState.OK
            }
            val n = installation!!.steps.lastIndex
            publish(installation!!.copy(state = InstallationState.RESTARTING, steps = replace(n, StepState.RUNNING, component.switchEffect)))
            switchTo(component, target)
        } catch (e: Exception) {
            delete(temporary)
            val message = e.message ?: e.javaClass.simpleName
            publish(installation!!.copy(state = InstallationState.FAILED, message = message, finishedAt = clock.instant()))
            log("installation of $target failed: $message")
        }
    }

    private fun step(index: Int, block: () -> Pair<String, StepState>) {
        publish(installation!!.copy(steps = replace(index, StepState.RUNNING, "")))
        val (detail, state) = try {
            block()
        } catch (e: Exception) {
            publish(installation!!.copy(steps = replace(index, StepState.FAILED, e.message ?: e.javaClass.simpleName)))
            throw e
        }
        publish(installation!!.copy(steps = replace(index, state, detail)))
    }

    private fun detail(index: Int, detail: String) =
        publish(installation!!.copy(steps = replace(index, StepState.RUNNING, detail)))

    private fun replace(index: Int, state: StepState, detail: String): List<Step> =
        installation!!.steps.mapIndexed { i, e -> if (i == index) e.copy(state = state, detail = detail) else e }

    private fun publish(i: Installation) {
        installation = i
        runCatching { directory.writeInstallation(InstallationJson.toJson(i)) }
    }

    // ------------------------------------------------------------------ switch

    /**
     * Every minute: when the shared folder holds a version this program doesn't run yet
     * (installed by another server sharing the folder, or here before a restart), switch to it.
     */
    internal fun follow() {
        val component = local ?: return
        val current = directory.current()
        val inService = component.versionInDirectory(directory)
        finish(component, inService)
        if (current == null || current == inService || switching.get() || running.get()) {
            pending = null
            return
        }
        if (component.blocker() != null || !directory.complete(current)) {
            pending = current
            return
        }
        pending = null
        log("$current installed from another server: switching ${component.component.label}")
        installed()?.let { keep(component, it.tag) }
        switchTo(component, current)
    }

    /** The installation this program restarted for is done once it runs the new version. */
    private fun finish(component: LocalComponent, inService: String?) {
        val i = directory.readInstallation()?.let { InstallationJson.fromJson(it) } ?: return
        if (i.state != InstallationState.RESTARTING || i.component != component.component) return
        val last = i.steps.lastIndex
        when {
            inService == i.target -> publish(i.copy(
                state = InstallationState.DONE, finishedAt = clock.instant(), message = "Version ${i.target} in service",
                steps = i.steps.mapIndexed { n, e -> if (n == last) e.copy(state = StepState.OK, detail = "Version ${i.target} in service") else e },
            ))
            Duration.between(i.startedAt, clock.instant()) > RESTART_TIMEOUT && !switching.get() -> publish(i.copy(
                state = InstallationState.FAILED, finishedAt = clock.instant(),
                message = "${component.component.label} still runs ${inService ?: buildVersion ?: BuildInfo.text}: check the restart",
                steps = i.steps.mapIndexed { n, e -> if (n == last) e.copy(state = StepState.FAILED, detail = "No restart on ${i.target}") else e },
            ))
        }
    }

    /** Keeps the files this program runs from in updates/<from>; the error message, or null. */
    private fun keep(component: LocalComponent, from: String): String? = try {
        val kept = directory.directory(from)
        Files.createDirectories(kept)
        component.keepCurrent(kept)
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    private fun switchTo(component: LocalComponent, tag: String) {
        if (!switching.compareAndSet(false, true)) return
        Thread({
            try {
                Thread.sleep(switchDelay.toMillis())
                component.switchTo(directory.directory(tag), tag)
            } catch (e: Exception) {
                switching.set(false)
                val message = "Switching to $tag failed: ${e.message ?: e.javaClass.simpleName}"
                log(message)
                installation?.takeIf { it.target == tag && it.state == InstallationState.RESTARTING }?.let {
                    publish(it.copy(state = InstallationState.FAILED, message = message, finishedAt = clock.instant(),
                        steps = it.steps.mapIndexed { n, e2 -> if (n == it.steps.lastIndex) e2.copy(state = StepState.FAILED, detail = message) else e2 }))
                }
            }
        }, "thermobox-switch").apply { isDaemon = true }.start()
    }

    private fun delete(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun log(message: String) = println("[ThermoBox updates] $message")

    companion object {
        private val STEPS = listOf(
            "Database backup",
            "Download",
            "File check",
            "Keeping the current version",
            "Putting in place",
        )
        private const val DISK_MARGIN = 50L * 1024 * 1024
        private const val MAX_OLDER = 20
        private val RESTART_TIMEOUT: Duration = Duration.ofMinutes(10)
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        fun mb(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
