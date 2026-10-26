package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Component
import ax.ibr.thermobox.common.update.Step
import ax.ibr.thermobox.common.update.StepState
import ax.ibr.thermobox.common.update.InstallationState
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.UpdateType
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

/** [Installation] ⇄ installation.json (Jackson tree: the Common classes have no Jackson mapping). */
internal object InstallationJson {

    fun toJson(i: Installation): ObjectNode = UpdateDirectory.json.createObjectNode().apply {
        put("id", i.id)
        put("target", i.target)
        put("from", i.from)
        put("type", i.type.name)
        put("component", i.component.name)
        put("user", i.user)
        put("startedAt", i.startedAt.toString())
        put("state", i.state.name)
        put("message", i.message)
        put("backup", i.backup)
        put("finishedAt", i.finishedAt?.toString())
        val steps = putArray("steps")
        i.steps.forEach { e -> steps.addObject().put("title", e.title).put("state", e.state.name).put("detail", e.detail) }
    }

    /**
     * Also reads the file written before 26.11 (French keys and enum names): during an update,
     * the two programs may not run the same version yet.
     */
    fun fromJson(n: ObjectNode): Installation? = runCatching {
        fun node(j: com.fasterxml.jackson.databind.JsonNode, key: String) =
            j.path(key).takeUnless { it.isMissingNode } ?: j.path(LEGACY_KEYS[key] ?: key)
        fun t(c: String): String? = node(n, c).takeUnless { it.isNull || it.isMissingNode }?.asText()
        fun e(c: String?): String = LEGACY_VALUES[c] ?: c!!
        Installation(
            id = t("id")!!,
            target = t("target")!!,
            from = t("from"),
            type = UpdateType.valueOf(e(t("type"))),
            component = Component.valueOf(t("component")!!),
            user = t("user").orEmpty(),
            startedAt = Instant.parse(t("startedAt")),
            state = InstallationState.valueOf(e(t("state"))),
            steps = node(n, "steps").map { s ->
                Step(node(s, "title").asText(), StepState.valueOf(e(node(s, "state").asText())), node(s, "detail").asText(""))
            },
            message = t("message").orEmpty(),
            backup = t("backup"),
            finishedAt = t("finishedAt")?.let { Instant.parse(it) },
        )
    }.getOrNull()

    private val LEGACY_KEYS = mapOf(
        "target" to "cible", "from" to "depuis", "component" to "composant", "user" to "utilisateur",
        "startedAt" to "demarreeLe", "state" to "etat", "backup" to "sauvegarde", "finishedAt" to "termineeLe",
        "steps" to "etapes", "title" to "titre",
    )

    private val LEGACY_VALUES = mapOf(
        "CORRECTIVE" to "PATCH", "FONCTIONNELLE" to "FEATURE",
        "EN_ATTENTE" to "PENDING", "EN_COURS" to "RUNNING", "IGNOREE" to "SKIPPED", "ECHEC" to "FAILED",
        "REDEMARRAGE" to "RESTARTING", "TERMINEE" to "DONE",
    )
}
