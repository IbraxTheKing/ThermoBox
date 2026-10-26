
package ax.ibr.thermobox.common.entities

enum class UserType {
    ADMIN, MANAGER, VIEWER;

    fun hasRole(role: String): Boolean {
        return when (this) {
            ADMIN -> true // An ADMIN may do everything
            MANAGER -> role == "MANAGER" || role == "VIEWER"
            VIEWER -> role == "VIEWER"
        }
    }
}