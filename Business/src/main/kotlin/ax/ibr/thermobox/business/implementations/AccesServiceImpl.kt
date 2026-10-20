package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.acces.Perimetre
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.services.AccesService
import ax.ibr.thermobox.common.services.HierarchieService
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.AccesDataService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

/** See [AccesService] for the rules. */
class AccesServiceImpl(
    private val acces: AccesDataService = PersistenceFactory.getAccesDataService(),
    private val hierarchie: HierarchieService = BusinessFactory.getHierarchieService(),
    private val salles: SalleService = BusinessFactory.getSalleService(),
    private val salleTemps: () -> SalleTempAttrService = { BusinessFactory.getSalleTempAttrService() },
) : AccesService {

    override fun estRestreint(user: User): Boolean = user.type != UserType.ADMIN && acces.estRestreint(user)

    override fun getAutorisations(user: User): List<Salle> = acces.getByUser(user).map { it.salle }

    override fun definir(user: User, restreint: Boolean, salles: Collection<Salle>) {
        require(user.type != UserType.ADMIN || !restreint) { "Un administrateur a toujours accès à tout" }
        val ids = salles.mapNotNull { it.id }
        ids.forEach { requireNotNull(this.salles.getById(it)) { "Élément $it introuvable" } }
        acces.remplacer(user, restreint, if (restreint) ids else emptyList())
    }

    override fun initialiser(user: User) {
        if (user.id == null || user.type == UserType.ADMIN) return
        acces.remplacer(user, true, emptyList())
    }

    override fun perimetre(user: User?): Perimetre {
        if (user == null) return Perimetre.AUCUN
        if (!estRestreint(user)) return Perimetre.TOUT
        val accessibles = HashSet<Long>()
        val visibles = HashSet<Long>()
        for (salle in getAutorisations(user)) {
            descendre(salle, accessibles)
            hierarchie.getChemin(salle).mapNotNullTo(visibles) { it.id }
        }
        return Perimetre(false, accessibles, visibles)
    }

    // The element and everything below it. Guards against a cycle in corrupted data.
    private fun descendre(salle: Salle, ids: MutableSet<Long>) {
        val id = salle.id ?: return
        if (!ids.add(id)) return
        hierarchie.getChildren(salle).forEach { descendre(it, ids) }
    }

    override fun filtrerTemperatures(perimetre: Perimetre, temperatures: List<Temperature>): List<Temperature> {
        if (perimetre.tout) return temperatures
        // Temperature → the salle or box it belongs to
        val autorisees = salleTemps().getAll().filter { perimetre.peutVoir(it.salle) }.mapNotNull { it.temperature.id }.toSet()
        return temperatures.filter { it.id in autorisees }
    }

    override fun oublierSalle(salle: Salle) = acces.supprimerPourSalle(salle)

    override fun oublierUtilisateur(user: User) = acces.supprimerPourUtilisateur(user)
}
