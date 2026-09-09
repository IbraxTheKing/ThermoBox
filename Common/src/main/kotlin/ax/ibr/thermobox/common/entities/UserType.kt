
package ax.ibr.thermobox.common.entities

enum class UserType {
    ADMIN, GESTIONNAIRE, VIEWER;

    fun hasRole(role: String): Boolean {
        return when (this) {
            ADMIN -> true // L'ADMIN peut tout faire
            GESTIONNAIRE -> role == "GESTIONNAIRE" || role == "VIEWER"
            VIEWER -> role == "VIEWER"
        }
    }
}