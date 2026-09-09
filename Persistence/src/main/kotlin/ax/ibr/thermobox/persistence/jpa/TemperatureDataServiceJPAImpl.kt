package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService
import ax.ibr.utils.services.jpa.CrudJpaService
import jakarta.persistence.EntityManager

class TemperatureDataServiceJPAImpl(pu: String, em: EntityManager,
                                    entityClass: Class<Temperature>
) : TemperatureDataService, CrudJpaService<Temperature>(em, entityClass) {
}