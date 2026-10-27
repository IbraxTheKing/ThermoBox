package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.entities.Groupe
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.User
import ax.ibr.utils.services.CrudService

/** Rows of "groupe", "groupe_membre" and "groupe_acces". */
interface GroupeDataService : CrudService<Groupe> {

    fun getByNom(nom: String): Groupe?

    fun getMembres(groupe: Groupe): List<User>

    fun getElements(groupe: Groupe): List<Salle>

    /** Groups [user] belongs to. */
    fun getGroupesDe(user: User): List<Groupe>

    /** Elements of every group [user] belongs to. */
    fun getElementsDesGroupesDe(user: User): List<Salle>

    /** Replaces, in one transaction, the members and the elements of [groupe]. */
    fun remplacer(groupe: Groupe, membres: Collection<Long>, elements: Collection<Long>)

    fun ajouterMembres(groupe: Groupe, membres: Collection<Long>)

    fun retirerMembres(groupe: Groupe, membres: Collection<Long>)

    fun ajouterElements(groupe: Groupe, elements: Collection<Long>)

    fun retirerElements(groupe: Groupe, elements: Collection<Long>)

    /** Deletes the group with its members and elements. */
    fun supprimer(groupe: Groupe)

    fun oublierUtilisateur(user: User)

    fun oublierSalle(salle: Salle)
}
