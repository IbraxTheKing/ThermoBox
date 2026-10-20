package ax.ibr.thermobox.common.update

import java.time.Instant

enum class TypeMiseAJour(val label: String, val description: String) {
    CORRECTIVE("Corrective", "Corrections de bugs et de sécurité de la même série, sans nouvelle fonctionnalité ni changement de la base de données : installation recommandée."),
    FONCTIONNELLE("Fonctionnelle", "Nouvelle série mensuelle : peut modifier la base de données et ne pas permettre de retour arrière."),
}

/** Les deux programmes qui se mettent à jour : chacun applique lui-même la nouvelle version. */
enum class Composant(val label: String) {
    REST("Serveur REST"),
    JSF("Interface JSF"),
}

enum class Canal { STABLE, PRERELEASE }

/** Fichier publié avec une version GitHub. [sha256] : empreinte publiée par GitHub, en hexadécimal. */
data class Artefact(val nom: String, val url: String, val taille: Long, val sha256: String?)

/** Une version publiée sur GitHub, plus récente que celle installée. */
data class VersionDisponible(
    val version: Version,
    val type: TypeMiseAJour,
    val titre: String,
    /** Notes de version (changelog), en Markdown. */
    val notes: String,
    val publieeLe: Instant?,
    val page: String,
    val artefacts: List<Artefact>,
    /** Opérations demandées par les notes (section « Migration »), null s'il n'y en a pas. */
    val migration: String?,
    /** Les notes annoncent une incompatibilité (base de données, configuration…). */
    val incompatible: Boolean,
    /** Plus ancienne que la version installée : revenir dessus est un retour arrière. */
    val anterieure: Boolean = false,
)

enum class NiveauControle { OK, INFO, AVERTISSEMENT, BLOQUANT }

data class Controle(val niveau: NiveauControle, val titre: String, val detail: String = "")

/** Ce qu'il faut savoir avant d'installer [version] : contrôles, risques, sauvegarde. */
data class Preparation(
    val version: VersionDisponible,
    val installee: Version?,
    val controles: List<Controle>,
    /** Une sauvegarde de la base est faite d'office (mise à jour fonctionnelle ou risquée). */
    val sauvegardeObligatoire: Boolean,
    /** Revenir à la version installée en remettant ses fichiers suffit (aucune migration annoncée). */
    val retourSimple: Boolean,
    /** L'administrateur doit cocher qu'il a lu les avertissements. */
    val confirmationRequise: Boolean,
) {
    val installable: Boolean get() = controles.none { it.niveau == NiveauControle.BLOQUANT }
}

data class OptionsInstallation(
    val sauvegarderBase: Boolean = true,
    /** Avertissements lus et acceptés (obligatoire quand [Preparation.confirmationRequise]). */
    val risquesAcceptes: Boolean = false,
)

enum class EtatEtape { EN_ATTENTE, EN_COURS, OK, IGNOREE, ECHEC }

data class Etape(val titre: String, val etat: EtatEtape = EtatEtape.EN_ATTENTE, val detail: String = "")

enum class EtatInstallation(val label: String) {
    EN_COURS("En cours"),
    /** Fichiers en place : le composant redémarre sur la nouvelle version. */
    REDEMARRAGE("Redémarrage"),
    TERMINEE("Terminée"),
    ECHEC("Échec"),
}

/** Déroulement d'une installation, partagé entre REST et JSF par le dossier de données. */
data class Installation(
    val id: String,
    val cible: String,
    val depuis: String?,
    val type: TypeMiseAJour,
    val composant: Composant,
    val utilisateur: String,
    val demarreeLe: Instant,
    val etat: EtatInstallation,
    val etapes: List<Etape>,
    val message: String = "",
    /** Fichier de sauvegarde de la base, s'il y en a un. */
    val sauvegarde: String? = null,
    val termineeLe: Instant? = null,
)

/** Comment ce serveur peut installer une version ; [detail] explique quoi faire sinon. */
data class ModeInstallation(val automatique: Boolean, val detail: String)

data class EtatMisesAJour(
    val composant: Composant?,
    /** null : build de développement, sans numéro de version. */
    val installee: Version?,
    val canal: Canal,
    val verificationAutomatique: Boolean,
    val derniereVerification: Instant?,
    /** Dernière vérification échouée, null si elle a réussi. */
    val erreur: String?,
    /** Plus récentes que la version installée, les plus récentes d'abord. */
    val disponibles: List<VersionDisponible>,
    /** Canal prerelease : versions plus anciennes, réinstallables (retour arrière). */
    val anterieures: List<VersionDisponible>,
    val mode: ModeInstallation,
    val installation: Installation?,
    /** Version mise en place par l'autre composant, que celui-ci n'a pas encore pu appliquer. */
    val enAttente: String?,
) {
    /** Dernière corrective de la série installée. */
    val corrective: VersionDisponible? get() = disponibles.firstOrNull { it.type == TypeMiseAJour.CORRECTIVE }

    /** Dernière version d'une série plus récente. */
    val fonctionnelle: VersionDisponible? get() = disponibles.firstOrNull { it.type == TypeMiseAJour.FONCTIONNELLE }

    val miseAJourDisponible: Boolean get() = disponibles.isNotEmpty()
}

interface UpdateService {

    fun getEtat(): EtatMisesAJour

    /** Interroge GitHub maintenant. Une erreur est gardée dans [EtatMisesAJour.erreur], pas levée. */
    fun verifier(): EtatMisesAJour

    /** Contrôles et risques avant d'installer [tag] (une version de [EtatMisesAJour.disponibles]). */
    fun preparer(tag: String): Preparation

    /**
     * Lance l'installation de [tag] en arrière-plan et rend son état de départ ; la suite se lit
     * dans [EtatMisesAJour.installation].
     * @throws IllegalArgumentException version inconnue ou avertissements non acceptés
     * @throws IllegalStateException installation impossible ici ou déjà en cours
     */
    fun installer(tag: String, utilisateur: String, options: OptionsInstallation): Installation
}
