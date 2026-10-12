package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.rules.HierarchieRules
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleSalleAttr
import ax.ibr.thermobox.common.services.SalleSalleAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.io.Serializable

class SalleSalleAttrServiceImpl(
    private val salleSalleAttrService: SalleSalleAttrService = PersistenceFactory.getSalleSalleAttrDataService()
) : SalleSalleAttrService, Serializable {

    override fun getBySalle(s: Salle): List<SalleSalleAttr> {
        return salleSalleAttrService.getBySalle(s)
    }

    override fun getChildren(parent: Salle): List<Salle> {
        return salleSalleAttrService.getChildren(parent)
    }

    override fun getParent(child: Salle): Salle? {
        return salleSalleAttrService.getParent(child)
    }

    /** @throws IllegalArgumentException if the link breaks the hierarchy rules. */
    override fun add(t: SalleSalleAttr) {
        verification(t)
        salleSalleAttrService.add(t)
    }

    /** @throws IllegalArgumentException if the link breaks the hierarchy rules. */
    override fun update(t: SalleSalleAttr) {
        verification(t)
        salleSalleAttrService.update(t)
    }

    override fun remove(t: SalleSalleAttr) {
        salleSalleAttrService.remove(t)
    }

    override fun getAll(): List<SalleSalleAttr> {
        return salleSalleAttrService.getAll()
    }

    override fun getById(id: Long): SalleSalleAttr? {
        return salleSalleAttrService.getById(id)
    }

    private fun verification(t: SalleSalleAttr) {
        HierarchieRules.checkLink(t.salleA, t.salleB)

        // A child has a single parent (e.g. an étage belongs to one bâtiment only).
        val otherParent = getBySalle(t.salleB)
            .firstOrNull { it.salleB.id == t.salleB.id && it.id != t.id }
            ?.salleA
        require(otherParent == null) {
            "« ${t.salleB.name} » est déjà rattaché à « ${otherParent?.name} »."
        }
    }
}
