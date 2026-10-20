package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Composant
import ax.ibr.thermobox.common.update.Etape
import ax.ibr.thermobox.common.update.EtatEtape
import ax.ibr.thermobox.common.update.EtatInstallation
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.TypeMiseAJour
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

/** [Installation] ⇄ installation.json (Jackson tree: the Common classes have no Jackson mapping). */
internal object InstallationJson {

    fun versJson(i: Installation): ObjectNode = DossierMisesAJour.json.createObjectNode().apply {
        put("id", i.id)
        put("cible", i.cible)
        put("depuis", i.depuis)
        put("type", i.type.name)
        put("composant", i.composant.name)
        put("utilisateur", i.utilisateur)
        put("demarreeLe", i.demarreeLe.toString())
        put("etat", i.etat.name)
        put("message", i.message)
        put("sauvegarde", i.sauvegarde)
        put("termineeLe", i.termineeLe?.toString())
        val etapes = putArray("etapes")
        i.etapes.forEach { e -> etapes.addObject().put("titre", e.titre).put("etat", e.etat.name).put("detail", e.detail) }
    }

    fun depuisJson(n: ObjectNode): Installation? = runCatching {
        fun t(c: String): String? = n.path(c).takeUnless { it.isNull || it.isMissingNode }?.asText()
        Installation(
            id = t("id")!!,
            cible = t("cible")!!,
            depuis = t("depuis"),
            type = TypeMiseAJour.valueOf(t("type")!!),
            composant = Composant.valueOf(t("composant")!!),
            utilisateur = t("utilisateur").orEmpty(),
            demarreeLe = Instant.parse(t("demarreeLe")),
            etat = EtatInstallation.valueOf(t("etat")!!),
            etapes = n.path("etapes").map { e ->
                Etape(e.path("titre").asText(), EtatEtape.valueOf(e.path("etat").asText()), e.path("detail").asText(""))
            },
            message = t("message").orEmpty(),
            sauvegarde = t("sauvegarde"),
            termineeLe = t("termineeLe")?.let { Instant.parse(it) },
        )
    }.getOrNull()
}
