package ax.ibr.thermobox.restserver.filters

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import jakarta.ws.rs.ext.WriterInterceptor
import jakarta.ws.rs.ext.WriterInterceptorContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The API as it was before 26.11, with French names, kept for the clients written against it.
 *
 * The English API lives under `api/v2/`. Any other path under `api/` is the old one: the request
 * is rewritten to its `api/v2/` equivalent (`api/hierarchie/arbre` → `api/v2/hierarchy/tree`),
 * and the JSON is translated both ways, keys and enum values (`{"salle": …}` ↔ `{"room": …}`,
 * `"BATIMENT"` ↔ `"BUILDING"`). Free texts (error messages, labels other than [VALUES]) are
 * English in both APIs.
 */
object LegacyApi {

    /** Request property set on the requests of the old API. */
    const val PROPERTY = "ax.ibr.thermobox.legacyApi"

    /** First segment after `api/`. */
    val ROOTS = mapOf(
        "climat" to "climate",
        "favoris" to "favorites",
        "hierarchie" to "hierarchy",
        "salles" to "rooms",
        "salletemps" to "roomtemps",
        "reglages" to "settings",
    )

    /** Any later segment. */
    val SEGMENTS = mapOf(
        "historique" to "history",
        "moyennes" to "averages",
        "consigne" to "setpoint",
        "consignes" to "setpoints",
        "mesurer" to "measurement",
        "arbre" to "tree",
        "acces" to "access",
    )

    /** Segments followed by a free value (a user or room name), which is never translated. */
    private val BEFORE_FREE_VALUE = setOf("username", "name")

    /** JSON keys, English → French. */
    val KEYS = mapOf(
        "all" to "tout",
        "ancestors" to "ancetres",
        "attached" to "rattache",
        "average" to "moyenne",
        "children" to "enfants",
        "devices" to "boitiers",
        "devicesVisible" to "boitiersVisibles",
        "failures" to "echecs",
        "favorite" to "favori",
        "inherited" to "heritee",
        "kept" to "conservees",
        "lastMeasurement" to "derniereMesure",
        "measurements" to "mesures",
        "nodes" to "noeuds",
        "ownSetpoints" to "consignesPropres",
        "replaced" to "remplacees",
        "room" to "salle",
        "rooms" to "salles",
        "setpoint" to "consigne",
        "setpoints" to "consignes",
    )

    /** Values of some keys, English → French. */
    val VALUES = mapOf(
        "kind" to mapOf("BUILDING" to "BATIMENT", "FLOOR" to "ETAGE", "ROOM" to "SALLE", "DEVICE" to "BOITIER"),
        "type" to mapOf("MANAGER" to "GESTIONNAIRE", "PATCH" to "CORRECTIVE", "FEATURE" to "FONCTIONNELLE"),
        "state" to mapOf(
            "PENDING" to "EN_ATTENTE", "RUNNING" to "EN_COURS", "SKIPPED" to "IGNOREE", "FAILED" to "ECHEC",
            "RESTARTING" to "REDEMARRAGE", "DONE" to "TERMINEE",
        ),
        "level" to mapOf("WARNING" to "AVERTISSEMENT", "BLOCKING" to "BLOQUANT"),
        "style" to mapOf("glass" to "verre", "panel" to "panneau"),
        "typeLabel" to mapOf("Patch" to "Corrective", "Feature" to "Fonctionnelle"),
    )

    private val KEYS_IN = KEYS.entries.associate { (en, fr) -> fr to en }
    // Old clients sent enum values in any case ("salle", "Gestionnaire").
    private val VALUES_IN = VALUES.mapValues { (_, m) -> m.entries.associate { (en, fr) -> fr.lowercase() to en } }

    private val json = ObjectMapper()

    /** `api/hierarchie/elements/3` → `api/v2/hierarchy/elements/3`; null for a path of the English API. */
    fun englishPath(path: String): String? {
        val segments = path.trim('/').split('/')
        if (segments.size < 2 || segments[0] != "api" || segments[1] == "v2") return null
        val out = ArrayList<String>(segments.size + 1)
        out += "api"
        out += "v2"
        out += ROOTS[segments[1]] ?: segments[1]
        for (i in 2 until segments.size) {
            val s = segments[i]
            out += if (segments[i - 1] in BEFORE_FREE_VALUE) s else SEGMENTS[s] ?: s
        }
        return out.joinToString("/")
    }

    /** JSON of the English API → JSON of the old one. */
    fun toLegacy(node: JsonNode): JsonNode = translate(node, null, out = true)

    /** JSON sent to the old API → JSON of the English one. */
    fun fromLegacy(node: JsonNode): JsonNode = translate(node, null, out = false)

    fun toLegacy(body: ByteArray): ByteArray = json.writeValueAsBytes(toLegacy(json.readTree(body)))

    fun fromLegacy(body: ByteArray): ByteArray = json.writeValueAsBytes(fromLegacy(json.readTree(body)))

    private fun translate(node: JsonNode, key: String?, out: Boolean): JsonNode = when {
        node.isObject -> {
            val result = json.createObjectNode()
            node.fields().forEach { (k, v) ->
                val newKey = (if (out) KEYS[k] else KEYS_IN[k]) ?: k
                // Value maps are named after the English key
                result.set<JsonNode>(newKey, translate(v, if (out) k else newKey, out))
            }
            result
        }
        node.isArray -> json.createArrayNode().also { a -> node.forEach { a.add(translate(it, key, out)) } }
        node.isTextual && key != null -> {
            val text = node.asText()
            val mapped = if (out) VALUES[key]?.get(text) else VALUES_IN[key]?.get(text.lowercase())
            if (mapped != null) TextNode(mapped) else node
        }
        else -> node
    }

    fun isJson(type: MediaType?): Boolean =
        type != null && type.type == "application" && (type.subtype == "json" || type.subtype.endsWith("+json"))
}

/** Rewrites the paths of the old API before Jersey looks for the resource. */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
class LegacyApiRequestFilter : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val english = LegacyApi.englishPath(requestContext.uriInfo.path) ?: return
        requestContext.setProperty(LegacyApi.PROPERTY, true)
        val uri = requestContext.uriInfo.baseUriBuilder.path(english)
            .replaceQuery(requestContext.uriInfo.requestUri.rawQuery)
            .build()
        requestContext.setRequestUri(requestContext.uriInfo.baseUri, uri)
    }
}

/** Translates the JSON bodies of the old API: French in, French out. */
@Provider
class LegacyApiInterceptor : ReaderInterceptor, WriterInterceptor {

    override fun aroundReadFrom(context: ReaderInterceptorContext): Any? {
        if (context.getProperty(LegacyApi.PROPERTY) == true && LegacyApi.isJson(context.mediaType)) {
            val body = context.inputStream.readBytes()
            if (body.isNotEmpty()) {
                context.inputStream = ByteArrayInputStream(runCatching { LegacyApi.fromLegacy(body) }.getOrDefault(body))
            } else {
                context.inputStream = ByteArrayInputStream(body)
            }
        }
        return context.proceed()
    }

    override fun aroundWriteTo(context: WriterInterceptorContext) {
        if (context.getProperty(LegacyApi.PROPERTY) != true || !LegacyApi.isJson(context.mediaType)) {
            context.proceed()
            return
        }
        val original = context.outputStream
        val buffer = ByteArrayOutputStream()
        context.outputStream = buffer
        try {
            context.proceed()
        } finally {
            context.outputStream = original
        }
        val body = buffer.toByteArray()
        original.write(if (body.isEmpty()) body else runCatching { LegacyApi.toLegacy(body) }.getOrDefault(body))
    }
}
