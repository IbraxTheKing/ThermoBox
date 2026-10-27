package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.common.update.Controle
import ax.ibr.thermobox.common.update.Etape
import ax.ibr.thermobox.common.update.EtatMisesAJour
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.TypeMiseAJour
import ax.ibr.thermobox.common.update.VersionDisponible

/**
 * JSON of /api/updates. Enums as names (CORRECTIVE / FONCTIONNELLE…), dates as ISO-8601 strings.
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
) {
    companion object {
        fun from(e: EtatMisesAJour, admin: Boolean) = UpdateStatusDto(
            component = e.composant?.name,
            installedVersion = e.installee?.tag,
            channel = e.canal.name,
            dailyCheck = e.verificationAutomatique,
            lastCheck = e.derniereVerification?.toString(),
            error = e.erreur.takeIf { admin },
            updateAvailable = e.miseAJourDisponible,
            latestPatch = e.corrective?.let { AvailableVersionDto.from(it) },
            latestFeature = e.fonctionnelle?.let { AvailableVersionDto.from(it) },
            available = e.disponibles.map { AvailableVersionDto.from(it) },
            olderVersions = e.anterieures.map { AvailableVersionDto.from(it) },
            automaticInstall = e.mode.automatique.takeIf { admin },
            installMode = e.mode.detail.takeIf { admin },
            installation = e.installation?.takeIf { admin }?.let { InstallationDto.from(it) },
        )
    }
}

data class AvailableVersionDto(
    val version: String,
    /** CORRECTIVE (same monthly series) or FONCTIONNELLE (new series, may be incompatible). */
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
    val incompatible: Boolean,
) {
    companion object {
        fun from(v: VersionDisponible) = AvailableVersionDto(
            version = v.version.tag, type = v.type.name, typeLabel = v.type.label,
            recommended = v.type == TypeMiseAJour.CORRECTIVE && !v.incompatible && !v.anterieure, prerelease = v.version.estPrerelease,
            channel = v.version.canal, older = v.anterieure,
            title = v.titre, publishedAt = v.publieeLe?.toString(), url = v.page, notes = v.notes,
            migration = v.migration, incompatible = v.incompatible,
        )
    }
}

data class CheckDto(val level: String, val title: String, val detail: String) {
    companion object {
        fun from(c: Controle) = CheckDto(c.niveau.name, c.titre, c.detail)
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
            AvailableVersionDto.from(p.version), p.installee?.tag, p.installable, p.controles.map { CheckDto.from(it) },
            p.sauvegardeObligatoire, p.retourSimple, p.confirmationRequise,
        )
    }
}

class InstallRequest {
    var backupDatabase: Boolean = true
    var acceptRisks: Boolean = false
}

data class StepDto(val title: String, val state: String, val detail: String) {
    companion object {
        fun from(e: Etape) = StepDto(e.titre, e.etat.name, e.detail)
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
    /** EN_COURS, REDEMARRAGE, TERMINEE or ECHEC. */
    val state: String,
    val message: String,
    val backupFile: String?,
    val finishedAt: String?,
    val steps: List<StepDto>,
) {
    companion object {
        fun from(i: Installation) = InstallationDto(
            i.id, i.cible, i.depuis, i.type.name, i.composant.name, i.utilisateur, i.demarreeLe.toString(), i.etat.name,
            i.message, i.sauvegarde, i.termineeLe?.toString(), i.etapes.map { StepDto.from(it) },
        )
    }
}
