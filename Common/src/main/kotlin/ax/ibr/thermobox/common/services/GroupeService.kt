package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User

/** A group with its members and the elements it gives access to. */
data class GroupeDetail(val groupe: Groupe, val membres: List<User>, val elements: List<Salle>)

/**
 * Groups of users (ADMIN). A member of a group sees the group's bâtiments, étages and salles
 * (and what they contain) on top of their own accesses, when their access is limited; a user who
 * sees everything is not affected. Accesses only add up: a group never hides anything.
 */
interface GroupeService {

    fun getAll(): List<Groupe>

    fun getById(id: Long): Groupe?

    fun detail(groupe: Groupe): GroupeDetail

    fun getGroupesDe(user: User): List<Groupe>

    /**
     * Creates or updates ([groupe] null: creates) a group with exactly these members and elements.
     * @throws IllegalArgumentException empty or already used name, unknown element, a box
     */
    fun enregistrer(groupe: Groupe?, nom: String, description: String?, membres: Collection<User>, elements: Collection<Salle>): Groupe

    fun ajouterMembres(groupe: Groupe, membres: Collection<User>)

    fun retirerMembres(groupe: Groupe, membres: Collection<User>)

    fun ajouterElements(groupe: Groupe, elements: Collection<Salle>)

    fun retirerElements(groupe: Groupe, elements: Collection<Salle>)

    fun supprimer(groupe: Groupe)
}
