package ax.ibr.thermobox.restserver.audit

import ax.ibr.thermobox.business.implementations.BusinessFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * What the log keeps about the target of a change: its name at the time (read before the change,
 * so a deleted element keeps its name), what it was before for some actions (the setpoint), and
 * the values sent without their secrets.
 */
object AuditTargets {

    private val json = ObjectMapper()

    /** Keys whose values never go to the log (passwords, webhook addresses, tokens…). */
    private val SECRET = Regex("(?i).*(password|secret|token|authorization|url|hash|key).*")

    /** The name of [type] [id] now; null when unknown or gone. */
    fun name(type: String, id: Long): String? = runCatching {
        when (type) {
            "element" -> BusinessFactory.getHierarchyCache().tree().elements[id]?.name
            "user" -> BusinessFactory.getUserService().getById(id)?.username
            "group" -> BusinessFactory.getUserGroupService().getById(id)?.name
            "channel" -> BusinessFactory.getAlertService().channel(id)?.channel?.name
            "rule" -> BusinessFactory.getAlertService().rule(id)?.name
            else -> null
        }
    }.getOrNull()

    /** « Bâtiment A › Étage 1 »: where an element is, null for another target. */
    fun place(type: String, id: Long): String? = runCatching {
        if (type != "element") return null
        val tree = BusinessFactory.getHierarchyCache().tree()
        val names = ArrayList<String>()
        var parent = tree.parents[id]
        while (parent != null && names.size < 16) {
            tree.elements[parent]?.name?.let { names.add(0, it) }
            parent = tree.parents[parent]
        }
        names.joinToString(" › ").takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** What [action] on [type] [id] changes, as it is before: the setpoint of an element. */
    fun before(action: String, type: String, id: Long): Map<String, Any?> = runCatching {
        when {
            action == "setpoint.set" && type == "element" -> {
                val element = BusinessFactory.getHierarchyCache().tree().elements[id] ?: return emptyMap()
                val own = BusinessFactory.getRoomTempAttrService().getLatestSetpoints(listOf(element))[id]?.value
                mapOf("setpoint" to own)
            }
            else -> emptyMap()
        }
    }.getOrDefault(emptyMap())

    /** [body] (JSON) without its secrets; null when it is not JSON. */
    fun sanitized(body: ByteArray): JsonNode? = runCatching { clean(json.readTree(body)) }.getOrNull()

    private fun clean(node: JsonNode): JsonNode = when {
        node.isObject -> json.createObjectNode().also { out ->
            node.fields().forEach { (k, v) -> if (!SECRET.matches(k)) out.set<JsonNode>(k, clean(v)) else if (!v.isNull) out.put(k, "•••") }
        }
        node.isArray -> json.createArrayNode().also { a -> node.forEach { a.add(clean(it)) } }
        node.isTextual && node.asText().length > 500 -> json.nodeFactory.textNode(node.asText().take(500) + "…")
        else -> node
    }

    /** The id and the name an answer gives (a creation: the element created). */
    fun fromAnswer(entity: Any?): Pair<Long?, String?> {
        if (entity == null || entity is ByteArray || entity is String) return null to null
        val tree = runCatching { tree(entity) }.getOrNull() as? ObjectNode ?: return null to null
        val id = tree.get("id")?.takeIf { it.canConvertToLong() }?.asLong()
        val name = listOf("name", "username").firstNotNullOfOrNull { k -> tree.get(k)?.takeIf { it.isTextual }?.asText() }
        return id to name
    }

    /**
     * [value] as JSON, written then read: not valueToTree, whose TokenBuffer is the part of
     * databind that needs a jackson-core of its very version (a mix of versions failed there).
     */
    private fun tree(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    fun details(changes: JsonNode?, before: Map<String, Any?>, place: String?): String? {
        if (changes == null && before.isEmpty() && place == null) return null
        val out = json.createObjectNode()
        if (changes != null) out.set<JsonNode>("changes", changes)
        if (before.isNotEmpty()) out.set<JsonNode>("before", tree(before))
        if (place != null) out.put("place", place)
        return json.writeValueAsString(out).take(60_000)
    }
}
