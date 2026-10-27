package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.services.GroupeDetail
import ax.ibr.thermobox.common.services.GroupeService
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.persistence.dataservices.GroupeDataService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

/** See [GroupeService]. */
class GroupeServiceImpl(
    private val groupes: GroupeDataService = PersistenceFactory.getGroupeDataService(),
    private val salles: SalleService = BusinessFactory.getSalleService(),
) : GroupeService {

    override fun getAll(): List<Groupe> = groupes.getAll().sortedBy { it.nom.lowercase() }

    override fun getById(id: Long): Groupe? = groupes.getById(id)

    override fun detail(groupe: Groupe): GroupeDetail =
        GroupeDetail(groupe, groupes.getMembres(groupe).sortedBy { it.username?.lowercase() }, groupes.getElements(groupe))

    override fun getGroupesDe(user: User): List<Groupe> = groupes.getGroupesDe(user).sortedBy { it.nom.lowercase() }

    override fun enregistrer(groupe: Groupe?, nom: String, description: String?, membres: Collection<User>, elements: Collection<Salle>): Groupe {
        val n = nom.trim()
        require(n.isNotEmpty()) { "Le nom du groupe est obligatoire" }
        require(n.length <= 100) { "Nom trop long (100 caractères au plus)" }
        groupes.getByNom(n)?.let { autre -> require(autre.id == groupe?.id) { "Un groupe s'appelle déjà « $n »" } }
        val ids = verifierElements(elements)
        val g = groupe?.id?.let { groupes.getById(it) } ?: Groupe(n)
        g.nom = n
        g.description = description?.trim()?.takeIf { it.isNotEmpty() }
        if (g.id == null) groupes.add(g) else groupes.update(g)
        val enregistre = g.id?.let { groupes.getById(it) } ?: groupes.getByNom(n) ?: error("Groupe non enregistré")
        groupes.remplacer(enregistre, membres.mapNotNull { it.id }, ids)
        return enregistre
    }

    override fun ajouterMembres(groupe: Groupe, membres: Collection<User>) = groupes.ajouterMembres(groupe, membres.mapNotNull { it.id })

    override fun retirerMembres(groupe: Groupe, membres: Collection<User>) = groupes.retirerMembres(groupe, membres.mapNotNull { it.id })

    override fun ajouterElements(groupe: Groupe, elements: Collection<Salle>) = groupes.ajouterElements(groupe, verifierElements(elements))

    override fun retirerElements(groupe: Groupe, elements: Collection<Salle>) = groupes.retirerElements(groupe, elements.mapNotNull { it.id })

    override fun supprimer(groupe: Groupe) = groupes.supprimer(groupe)

    // Accesses are given on bâtiments, étages and salles; a box follows its salle
    private fun verifierElements(elements: Collection<Salle>): List<Long> = elements.mapNotNull { it.id }.distinct().onEach { id ->
        val s = requireNotNull(salles.getById(id)) { "Élément $id introuvable" }
        require(s !is Boitier) { "« ${s.name} » est un boîtier : donnez accès à sa salle" }
    }
}
