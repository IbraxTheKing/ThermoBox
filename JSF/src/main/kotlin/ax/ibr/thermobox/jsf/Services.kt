package ax.ibr.thermobox.jsf

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Favorite
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.User


/** JSF talks to the Business layer directly (never through the REST API). */
object Services {
    val salles get() = BusinessFactory.getRoomService()
    val salleTemps get() = BusinessFactory.getRoomTempAttrService()
    val temperatures get() = BusinessFactory.getTemperatureService()
    val users get() = BusinessFactory.getUserService()
    val hierarchie get() = BusinessFactory.getHierarchyService()
    val climat get() = BusinessFactory.getClimateService()
    val favoris get() = BusinessFactory.getFavoriteService()
    val images get() = BusinessFactory.getRoomImageService()
    val acces get() = BusinessFactory.getAccessService()
    val groupes get() = BusinessFactory.getUserGroupService()

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

/** Ids of the salles [user] pinned. */
fun favorisDe(user: User?): Set<Long> =
    user?.let { u -> Services.favoris.getByUser(u).mapNotNull { it.room.id }.toSet() }.orEmpty()

/** Adds or removes [salle] from [user]'s favourites; returns whether it is now a favourite. */
fun basculerFavori(user: User, salle: Room): Boolean {
    val existant = Services.favoris.getByUser(user).firstOrNull { it.room.id == salle.id }
    if (existant != null) {
        Services.favoris.remove(existant)
        return false
    }
    Services.favoris.add(Favorite(user, salle))
    return true
}
