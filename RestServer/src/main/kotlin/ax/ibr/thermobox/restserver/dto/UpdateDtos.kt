package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.update.Check
import ax.ibr.thermobox.common.update.Step
import ax.ibr.thermobox.common.update.UpdateStatus
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.UpdateType
import ax.ibr.thermobox.common.update.AvailableVersion

/**
 * JSON of /api/v2/updates. Enums as names (PATCH / FEATURE…), dates as ISO-8601 strings.
 * Third-party applications use [UpdateStatusDto.updateAvailable], [UpdateStatusDto.latestPatch]
 * and [UpdateStatusDto.latestFeature] to notify their users.
 */
data class UpdateStatusDto(
    val component: String?,
    val installedVersion: String?,
    val channel: String,
    val dailyCheck: Boolean,
    val lastCheck: String?,
    /** Last check failure, ADMIN only. */
    val error: String?,
    val updateAvailable: Boolean,
    val latestPatch: AvailableVersionDto?,
    val latestFeature: AvailableVersionDto?,
    val available: List<AvailableVersionDto>,
    /** Prerelease channel only: older versions that can be installed again. */
    val olderVersions: List<AvailableVersionDto>,
    /** Whether this server can install by itself, and how; ADMIN only. */
    val automaticInstall: Boolean?,
    val installMode: String?,
    /** Last installation, ADMIN only. */
    val installation: InstallationDto?,
    /**
     * Can the installed version be left for an older one? From its release notes
     * ("Rétrocompatibilité : oui / non"); null when they don't say, or GitHub doesn't list it.
     */
    val installedRollback: Boolean?,
    /** Release notes of the installed version (Markdown), null if GitHub doesn't list it. */
    val installedNotes: String?,
) {
    companion object {
        fun from(e: UpdateStatus, admin: Boolean) = UpdateStatusDto(
            component = e.component?.name,
            installedVersion = e.installed?.tag,
            channel = e.channel.name,
            dailyCheck = e.automaticCheck,
            lastCheck = e.lastCheck?.toString(),
            error = e.error.takeIf { admin },
            updateAvailable = e.updateAvailable,
            latestPatch = e.latestPatch?.let { AvailableVersionDto.from(it) },
            latestFeature = e.latestFeature?.let { AvailableVersionDto.from(it) },
            available = e.available.map { AvailableVersionDto.from(it) },
            olderVersions = e.olderVersions.map { AvailableVersionDto.from(it) },
            automaticInstall = e.mode.automatic.takeIf { admin },
            installMode = e.mode.detail.takeIf { admin },
            installation = e.installation?.takeIf { admin }?.let { InstallationDto.from(it) },
            installedRollback = e.installedRollback,
            installedNotes = e.installedNotes,
        )
    }
}

data class AvailableVersionDto(
    val version: String,
    /** PATCH (same monthly series) or FEATURE (new series, may be incompatible). */
    val type: String,
    val typeLabel: String,
    /** Corrective: bug and security fixes only, the database is untouched; installing is recommended. */
    val recommended: Boolean,
    val prerelease: Boolean,
    /** stable, nightly, beta, rc… */
    val channel: String,
    /** Older than the installed version: installing it is a rollback. */
    val older: Boolean,
    val title: String,
    val publishedAt: String?,
    val url: String,
    /** Release notes (changelog), Markdown. */
    val notes: String,
    val migration: String?,
    /** The notes announce an incompatibility; for an older version, also when [blockedBy] is set. */
    val incompatible: Boolean,
    /** Older version only: the version, up to the installed one, whose notes forbid going back past it. */
    val blockedBy: String?,
) {
    companion object {
        fun from(v: AvailableVersion) = AvailableVersionDto(
            version = v.version.tag, type = v.type.name, typeLabel = v.type.label,
            recommended = v.type == UpdateType.PATCH && !v.incompatible && !v.older, prerelease = v.version.isPrerelease,
            channel = v.version.channel, older = v.older,
            title = v.title, publishedAt = v.publishedAt?.toString(), url = v.page, notes = v.notes,
            migration = v.migration, incompatible = v.incompatible, blockedBy = v.blockedBy?.tag,
        )
    }
}

data class CheckDto(val level: String, val title: String, val detail: String) {
    companion object {
        fun from(c: Check) = CheckDto(c.level.name, c.title, c.detail)
    }
}

data class PreparationDto(
    val version: AvailableVersionDto,
    val installedVersion: String?,
    val installable: Boolean,
    val checks: List<CheckDto>,
    /** The database is backed up whatever the request says. */
    val backupRequired: Boolean,
    /** Putting the current files back is enough to go back (no migration announced). */
    val simpleRollback: Boolean,
    /** "acceptRisks": true is required to install. */
    val confirmationRequired: Boolean,
) {
    companion object {
        fun from(p: Preparation) = PreparationDto(
            AvailableVersionDto.from(p.version), p.installed?.tag, p.installable, p.checks.map { CheckDto.from(it) },
            p.backupRequired, p.simpleRollback, p.confirmationRequired,
        )
    }
}

class InstallRequest {
    var backupDatabase: Boolean = true
    var acceptRisks: Boolean = false
}

data class StepDto(val title: String, val state: String, val detail: String) {
    companion object {
        fun from(e: Step) = StepDto(e.title, e.state.name, e.detail)
    }
}

data class InstallationDto(
    val id: String,
    val target: String,
    val from: String?,
    val type: String,
    val component: String,
    val user: String,
    val startedAt: String,
    /** RUNNING, RESTARTING, DONE or FAILED. */
    val state: String,
    val message: String,
    val backupFile: String?,
    val finishedAt: String?,
    val steps: List<StepDto>,
) {
    companion object {
        fun from(i: Installation) = InstallationDto(
            i.id, i.target, i.from, i.type.name, i.component.name, i.user, i.startedAt.toString(), i.state.name,
            i.message, i.backup, i.finishedAt?.toString(), i.steps.map { StepDto.from(it) },
        )
    }
}
