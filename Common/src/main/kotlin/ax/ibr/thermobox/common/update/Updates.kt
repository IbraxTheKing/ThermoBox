package ax.ibr.thermobox.common.update

import java.time.Instant

enum class UpdateType(val label: String, val description: String) {
    PATCH("Patch", "Bug and security fixes of the same series, with no new feature nor database change: installing it is recommended."),
    FEATURE("Feature", "New monthly series: may change the database and not allow going back."),
}

/** The program that updates itself (the JSF interface, removed in 26.12, was the other one). */
enum class Component(val label: String) {
    REST("REST server"),
}

enum class Channel { STABLE, PRERELEASE }

/** File published with a GitHub release. [sha256]: digest published by GitHub, in hexadecimal. */
data class Artifact(val name: String, val url: String, val size: Long, val sha256: String?)

/** A version published on GitHub, newer than the installed one. */
data class AvailableVersion(
    val version: Version,
    val type: UpdateType,
    val title: String,
    /** Notes de version (changelog), en Markdown. */
    val notes: String,
    val publishedAt: Instant?,
    val page: String,
    val artifacts: List<Artifact>,
    /** Operations the notes ask for ("Migration" section), null if there are none. */
    val migration: String?,
    /** The notes announce an incompatibility (database, configuration…). */
    val incompatible: Boolean,
    /** Older than the installed version: installing it is a rollback. */
    val older: Boolean = false,
)

enum class CheckLevel { OK, INFO, WARNING, BLOCKING }

data class Check(val level: CheckLevel, val title: String, val detail: String = "")

/** What to know before installing [version]: checks, risks, backup. */
data class Preparation(
    val version: AvailableVersion,
    val installed: Version?,
    val checks: List<Check>,
    /** A database backup is always made (feature or risky update). */
    val backupRequired: Boolean,
    /** Putting back the files of the installed version is enough to return to it (no migration announced). */
    val simpleRollback: Boolean,
    /** The administrator must tick that they read the warnings. */
    val confirmationRequired: Boolean,
) {
    val installable: Boolean get() = checks.none { it.level == CheckLevel.BLOCKING }
}

data class InstallOptions(
    val backupDatabase: Boolean = true,
    /** Warnings read and accepted (required when [Preparation.confirmationRequired]). */
    val risksAccepted: Boolean = false,
)

enum class StepState { PENDING, RUNNING, OK, SKIPPED, FAILED }

data class Step(val title: String, val state: StepState = StepState.PENDING, val detail: String = "")

enum class InstallationState(val label: String) {
    RUNNING("Running"),
    /** Files in place: the component restarts on the new version. */
    RESTARTING("Restarting"),
    DONE("Done"),
    FAILED("Failed"),
}

/** Progress of an installation, kept in the data folder. */
data class Installation(
    val id: String,
    val target: String,
    val from: String?,
    val type: UpdateType,
    val component: Component,
    val user: String,
    val startedAt: Instant,
    val state: InstallationState,
    val steps: List<Step>,
    val message: String = "",
    /** Database backup file, if there is one. */
    val backup: String? = null,
    val finishedAt: Instant? = null,
)

/** How this server can install a version; [detail] explains what to do otherwise. */
data class InstallMode(val automatic: Boolean, val detail: String)

data class UpdateStatus(
    val component: Component?,
    /** null: development build, without a version number. */
    val installed: Version?,
    val channel: Channel,
    val automaticCheck: Boolean,
    val lastCheck: Instant?,
    /** Last failed check, null if it succeeded. */
    val error: String?,
    /** Newer than the installed version, newest first. */
    val available: List<AvailableVersion>,
    /** Prerelease channel: older versions, which can be installed again (rollback). */
    val olderVersions: List<AvailableVersion>,
    val mode: InstallMode,
    val installation: Installation?,
    /** Version put in place by the other component, that this one could not apply yet. */
    val pending: String?,
) {
    /** Latest patch of the installed series. */
    val latestPatch: AvailableVersion? get() = available.firstOrNull { it.type == UpdateType.PATCH }

    /** Latest version of a newer series. */
    val latestFeature: AvailableVersion? get() = available.firstOrNull { it.type == UpdateType.FEATURE }

    val updateAvailable: Boolean get() = available.isNotEmpty()
}

interface UpdateService {

    fun getStatus(): UpdateStatus

    /** Asks GitHub now. An error is kept in [UpdateStatus.error], not thrown. */
    fun check(): UpdateStatus

    /** Checks and risks before installing [tag] (a version of [UpdateStatus.available]). */
    fun prepare(tag: String): Preparation

    /**
     * Starts installing [tag] in the background and returns its initial state; what follows is
     * read in [UpdateStatus.installation].
     * @throws IllegalArgumentException unknown version or warnings not accepted
     * @throws IllegalStateException installation impossible here or already running
     */
    fun install(tag: String, user: String, options: InstallOptions): Installation
}
