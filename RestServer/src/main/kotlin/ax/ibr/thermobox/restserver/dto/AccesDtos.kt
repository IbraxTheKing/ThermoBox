package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.entities.kind

/** An element a user is granted, with everything it contains. */
data class AccesElementDto(val id: Long?, val name: String?, val kind: String)

/**
 * GET /api/users/{id}/acces. restricted = false: the user sees everything (always the case of
 * an ADMIN); true: only [elements] and their content.
 */
data class AccesDto(val restricted: Boolean, val elements: List<AccesElementDto>) {
    companion object {
        fun of(user: User): AccesDto {
            val service = BusinessFactory.getAccesService()
            val restreint = user.type != UserType.ADMIN && service.estRestreint(user)
            val elements = if (restreint) service.getAutorisations(user).map { AccesElementDto(it.id, it.name, it.kind.name) } else emptyList()
            return AccesDto(restreint, elements)
        }
    }
}

/** PUT /api/users/{id}/acces: {"restricted": true, "elements": [ids of bâtiments, étages, salles]} */
class AccesRequest {
    var restricted: Boolean = true
    var elements: List<Long> = emptyList()
}
