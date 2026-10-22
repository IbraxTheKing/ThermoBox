package ax.ibr.thermobox.jsf.depreciation

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Named

/**
 * The JSF interface is deprecated in favour of the Angular client, and the JSF module is removed
 * in [VERSION_SUPPRESSION]. Admins are told so in the interface (deprecation-banner.xhtml) and in
 * the server log at startup.
 */
@Named("depreciation")
@ApplicationScoped
class Depreciation {
    val clientUrl: String get() = CLIENT_URL
    val clientBrancheUrl: String get() = CLIENT_BRANCHE_URL
    val clientBranche: String get() = CLIENT_BRANCHE
    val versionSuppression: String get() = VERSION_SUPPRESSION

    companion object {
        const val CLIENT_URL = "https://github.com/IbraxTheKing/Thermobox-Ng-Client"
        /** The client is only on its dev branch for now. */
        const val CLIENT_BRANCHE = "dev"
        const val CLIENT_BRANCHE_URL = "$CLIENT_URL/tree/$CLIENT_BRANCHE"
        const val VERSION_SUPPRESSION = "26.12"

        const val MESSAGE = "L'interface JSF de ThermoBox est dépréciée et sera supprimée dans la version " +
            "$VERSION_SUPPRESSION. Utilisez le client Angular : $CLIENT_URL (branche $CLIENT_BRANCHE pour l'instant)."
    }
}
