package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.rules.HierarchieRules
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.common.services.HierarchieService
import ax.ibr.thermobox.common.services.SalleSalleAttrService
import ax.ibr.thermobox.common.services.SalleService
import java.io.Serializable

class HierarchieServiceImpl(
    private val salles: SalleService = BusinessFactory.getSalleService(),
    private val liens: SalleSalleAttrService = BusinessFactory.getSalleSalleAttrService()
) : HierarchieService, Serializable {

    private val ordre = compareBy<Salle>({ it.kind }, { it.name?.lowercase() })

    override fun getRacines(): List<Salle> {
        val attaches = liens.getAll().mapNotNull { it.salleB.id }.toSet()
        return salles.getAll().filter { it.id !in attaches }.sortedWith(ordre)
    }

    override fun getChildren(parent: Salle): List<Salle> = liens.getChildren(parent).sortedWith(ordre)

    override fun getParent(child: Salle): Salle? = liens.getParent(child)

    override fun getChemin(salle: Salle): List<Salle> {
        val chemin = ArrayList<Salle>()
        var courant = getParent(salle)
        // The rules forbid cycles; the bound only protects against bad data in database.
        while (courant != null && chemin.size < MAX_DEPTH) {
            chemin.add(0, courant)
            courant = getParent(courant)
        }
        return chemin
    }

    override fun getBoitiers(salle: Salle): List<Boitier> {
        if (salle is Boitier) return listOf(salle)
        val boitiers = ArrayList<Boitier>()
        val vus = HashSet<Long?>()
        fun parcourir(s: Salle, profondeur: Int) {
            if (profondeur > MAX_DEPTH || !vus.add(s.id)) return
            for (enfant in getChildren(s)) {
                if (enfant is Boitier) boitiers.add(enfant) else parcourir(enfant, profondeur + 1)
            }
        }
        parcourir(salle, 0)
        return boitiers
    }

    override fun setParent(child: Salle, parent: Salle?) {
        val actuels = liens.getBySalle(child).filter { it.salleB.id == child.id }
        if (parent != null && actuels.size == 1 && actuels[0].salleA.id == parent.id) return

        // Check before touching anything, so a refused move keeps the current parent.
        if (parent != null) HierarchieRules.checkLink(parent, child)

        actuels.forEach { liens.remove(it) }
        if (parent == null) return
        try {
            liens.add(SalleSalleAttr(parent, child))
        } catch (e: Exception) {
            actuels.forEach { liens.add(SalleSalleAttr(it.salleA, it.salleB)) }
            throw e
        }
    }

    private companion object {
        /** Batiment → Etage → Salle → Boitier. */
        const val MAX_DEPTH = 4
    }
}
