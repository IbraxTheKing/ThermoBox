package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Etage
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

    override fun add(t: SalleSalleAttr) {
        try {
            if (verification(t))
                salleSalleAttrService.add(t)
            else {
                throw Exception("Error")
            }
        }
        catch (e: Exception) {
            throw e
        }
    }

    override fun update(t: SalleSalleAttr) {
        try {
            if (verification(t))
                salleSalleAttrService.update(t)
            else {
                throw Exception("Error")
            }
        }
        catch (e: Exception) {
            throw e
        }
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

    private fun verification(t: SalleSalleAttr) : Boolean {
        val a = t.salleA
        val b = t.salleB
        if (a is Etage && b is Etage) {
            throw Exception("Un etage ne peux pas avoir un etage.")
        }
        if (a is Batiment && b is Batiment) {
            throw Exception("Un batiment ne peux pas avoir un batiment.")
        }
        // TODO: Verifier si un etage est deja present dans un batiment, si oui interdire le fait de le mettre dans plusieurs batiments.
        if ((a is Batiment && b is Etage)) {
            return etageBatimentVerification(b)
        }
        if (a is Etage && b is Batiment)
            return etageBatimentVerification(a)

        return true
    }

    private fun etageBatimentVerification(b: Etage): Boolean {
        return getBySalle(b).none {
            it.salleA is Batiment || it.salleB is Batiment
        }
    }
}