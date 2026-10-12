package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User


/** JSF talks to the Business layer directly (never through the REST API). */
object Services {
    val salles get() = BusinessFactory.getSalleService()
    val salleTemps get() = BusinessFactory.getSalleTempAttrService()
    val temperatures get() = BusinessFactory.getTemperatureService()
    val users get() = BusinessFactory.getUserService()
    val hierarchie get() = BusinessFactory.getHierarchieService()
    val climat get() = BusinessFactory.getClimatService()

    val driver: ProtocolDriver get() = BusinessFactory.getDriver()
}

/** Une entité pas encore persistée a un id null (ou 0 selon le mapping). */
fun isNew(id: Long?): Boolean = id == null || id == 0L

/** True if another account already uses this username (the login would pick the wrong one). */
fun usernameTaken(user: User): Boolean {
    val name = user.username?.trim() ?: return false
    val other = Services.users.getByUsername(name) ?: return false
    return other.id != user.id
}

/** « Bâtiment A › Étage 2 », empty if the element is not attached. */
fun cheminLabel(salle: Salle): String =
    Services.hierarchie.getChemin(salle).joinToString(" › ") { it.name.orEmpty() }
