package ax.ibr.thermobox.persistence.jpa

import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.persistence.dataservices.UserDataService
import ax.ibr.utils.services.jpa.CrudJpaService

import jakarta.persistence.EntityManagerFactory

class UserDataServiceJPAImpl(emf: EntityManagerFactory,
                             entityClass: Class<User>
) : UserDataService, CrudJpaService<User>(emf, entityClass) {

    override fun getByUsername(username: String): User? = read { em ->
        em.createQuery(
            "SELECT u FROM User u WHERE u.username = :username",
            User::class.java
        ).setParameter("username", username)
            .resultList
            .firstOrNull()
    }

    override fun getByType(userType: UserType): List<User>? = read { em ->
        em.createQuery(
            "SELECT u FROM User u WHERE u.type = :type",
            User::class.java
        ).setParameter("type", userType)
            .resultList
    }
}
