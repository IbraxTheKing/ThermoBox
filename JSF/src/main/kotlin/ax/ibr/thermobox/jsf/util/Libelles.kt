package ax.ibr.thermobox.jsf.util

import ax.ibr.thermobox.common.entities.RoomKind
import ax.ibr.thermobox.common.update.Component
import ax.ibr.thermobox.common.update.InstallationState
import ax.ibr.thermobox.common.update.UpdateType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Named

/**
 * French texts of the core enums for the JSF views (the core labels are English): in EL
 * `#{libelles.kind(n.kind)}`, in Kotlin `Libelles.kind(kind)`.
 */
@Named("libelles")
@ApplicationScoped
class Libelles {
    fun kind(k: RoomKind?): String = Companion.kind(k)
    fun withArticle(k: RoomKind?): String = Companion.withArticle(k)
    fun updateType(t: UpdateType?): String = Companion.updateType(t)
    fun updateTypeDescription(t: UpdateType?): String = Companion.updateTypeDescription(t)
    fun component(c: Component?): String = Companion.component(c)
    fun installationState(s: InstallationState?): String = Companion.installationState(s)

    companion object {
        fun kind(k: RoomKind?): String = when (k) {
            RoomKind.BUILDING -> "Bâtiment"
            RoomKind.FLOOR -> "Étage"
            RoomKind.ROOM -> "Salle"
            RoomKind.DEVICE -> "Boîtier"
            null -> ""
        }

        fun withArticle(k: RoomKind?): String = when (k) {
            RoomKind.BUILDING -> "un bâtiment"
            RoomKind.FLOOR -> "un étage"
            RoomKind.ROOM -> "une salle"
            RoomKind.DEVICE -> "un boîtier"
            null -> ""
        }

        fun updateType(t: UpdateType?): String = when (t) {
            UpdateType.PATCH -> "Corrective"
            UpdateType.FEATURE -> "Fonctionnelle"
            null -> ""
        }

        fun updateTypeDescription(t: UpdateType?): String = when (t) {
            UpdateType.PATCH -> "Corrections de bugs et de sécurité de la même série, sans nouvelle fonctionnalité ni changement de la base de données : installation recommandée."
            UpdateType.FEATURE -> "Nouvelle série mensuelle : peut modifier la base de données et ne pas permettre de retour arrière."
            null -> ""
        }

        fun component(c: Component?): String = when (c) {
            Component.REST -> "Serveur REST"
            Component.JSF -> "Interface JSF"
            null -> ""
        }

        fun installationState(s: InstallationState?): String = when (s) {
            InstallationState.RUNNING -> "En cours"
            InstallationState.RESTARTING -> "Redémarrage"
            InstallationState.DONE -> "Terminée"
            InstallationState.FAILED -> "Échec"
            null -> ""
        }
    }
}
