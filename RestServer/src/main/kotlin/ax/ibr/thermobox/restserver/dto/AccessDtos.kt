package ax.ibr.thermobox.restserver.dto

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import ax.ibr.thermobox.common.entities.kind

/** An element a user is granted, with everything it contains. */
data class AccessElementDto(val id: Long?, val name: String?, val kind: String)

/**
 * GET /api/v2/users/{id}/access. restricted = false: the user sees everything (always the case of
 * an ADMIN); true: only [elements] and their content.
 */
data class AccessDto(val restricted: Boolean, val elements: List<AccessElementDto>) {
    companion object {
        fun of(user: User): AccessDto {
            val service = BusinessFactory.getAccessService()
            val restricted = user.type != UserType.ADMIN && service.isRestricted(user)
            val elements = if (restricted) service.getGrants(user).map { AccessElementDto(it.id, it.name, it.kind.name) } else emptyList()
            return AccessDto(restricted, elements)
        }
    }
}

/** PUT /api/v2/users/{id}/access: {"restricted": true, "elements": [ids of buildings, floors, rooms]} */
class AccessRequest {
    var restricted: Boolean = true
    var elements: List<Long> = emptyList()
}
