package org.example.ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory

class SalleTempAttrServiceImpl : SalleTempAttrService {

    private val salleTempAttrService: SalleTempAttrService = PersistenceFactory().getSalleTempAttrDataService()

    override fun getBySalle(salleId: Int): SalleTempAttr? {
        TODO("Not yet implemented")
    }

    override fun getBySalle(salle: Salle): SalleTempAttr? {
        TODO("Not yet implemented")
    }

    override fun add(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun update(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun remove(t: SalleTempAttr) {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<SalleTempAttr> {
        TODO("Not yet implemented")
    }

    override fun getById(id: Long): SalleTempAttr? {
        TODO("Not yet implemented")
    }

}
