package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.UserDataService
import ax.ibr.utils.services.jpa.CrudJpaService

import jakarta.persistence.EntityManager

class UserDataServiceJPAImpl(pu: String, em: EntityManager,
                             entityClass: Class<User>
) : UserDataService, CrudJpaService<User>(em, entityClass) {


    override fun getByUsername(username: String): User {
        return em.createQuery(
            "SELECT u FROM User u WHERE u.username = :username",
            User::class.java
        ).setParameter("username", username)
            .singleResult
    }

    override fun getByType(userType: UserType): List<User>? {
        return em.createQuery(
            "SELECT u FROM User u WHERE u.type = :type",
            User::class.java
        ).setParameter("type", userType)
            .resultList
    }
}