package ax.ibr.thermobox.business.update

import ax.ibr.thermobox.common.update.Canal
import ax.ibr.thermobox.common.update.Composant
import ax.ibr.thermobox.common.update.Controle
import ax.ibr.thermobox.common.update.Etape
import ax.ibr.thermobox.common.update.EtatEtape
import ax.ibr.thermobox.common.update.EtatInstallation
import ax.ibr.thermobox.common.update.EtatMisesAJour
import ax.ibr.thermobox.common.update.Installation
import ax.ibr.thermobox.common.update.ModeInstallation
import ax.ibr.thermobox.common.update.NiveauControle
import ax.ibr.thermobox.common.update.OptionsInstallation
import ax.ibr.thermobox.common.update.Preparation
import ax.ibr.thermobox.common.update.TypeMiseAJour
import ax.ibr.thermobox.common.update.UpdateService
import ax.ibr.thermobox.common.update.Version
import ax.ibr.thermobox.common.update.VersionDisponible
import ax.ibr.thermobox.persistence.dataservices.PersistenceFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub for new versions (once a day, or on demand) and installs one:
 * database backup, download checked by SHA-256, files put in the shared folder, then the
 * program switches to them ([ComposantLocal]). The other program (REST or JSF) notices the new
 * version in the shared folder and switches too, within a minute.
 */
class UpdateServiceImpl(
    private val config: ConfigMisesAJour = ConfigMisesAJour.depuisEnvironnement(),
    private val github: GitHubReleases = GitHubReleases(config),
    private val telechargeur: Telechargeur = Telechargeur(config),
    /** Writes a database dump to the file and describes it. */
    private val sauvegarderBase: (Path) -> String = { f ->
        PersistenceFactory.sauvegarderBase(f).let { "${it.tables} tables, ${it.lignes} lignes, ${mo(it.octets)}" }
    },
    private val horloge: Clock = Clock.systemUTC(),
    private val versionDuBuild: Version? = BuildInfo.version,
    private val javaDuServeur: Int = Runtime.version().feature(),
    /** Lets the HTTP response that started the installation reach the browser before the switch. */
    private val delaiBascule: Duration = Duration.ofSeconds(3),
) : UpdateService {

    val dossier = DossierMisesAJour(config.dossier)

    @Volatile private var local: ComposantLocal? = null
    @Volatile private var releases: List<ReleaseGitHub> = emptyList()
    @Volatile private var derniereVerification: Instant? = null
    @Volatile private var erreur: String? = null
    @Volatile private var installation: Installation? = null
    @Volatile private var enAttente: String? = null
    private val enCours = AtomicBoolean(false)
    private val bascule = AtomicBoolean(false)
    private var planificateur: ScheduledExecutorService? = null

    /** Called once by the program at startup: daily check, and follows the shared folder. */
    @Synchronized
    fun demarrer(composant: ComposantLocal, planifier: Boolean = true) {
        local = composant
        if (planificateur != null || !planifier) return
        val p = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "thermobox-mises-a-jour").apply { isDaemon = true } }
        if (config.verificationAutomatique) {
            p.scheduleAtFixedRate({ verifier() }, 1, config.intervalleVerification.toMinutes(), TimeUnit.MINUTES)
        }
        p.scheduleWithFixedDelay({ runCatching { suivre() }.onFailure { journal("suivi : ${it.message}") } }, 5, 60, TimeUnit.SECONDS)
        planificateur = p
    }

    /** At shutdown (and when Tomcat undeploys the WAR, so the old version's threads stop). */
    @Synchronized
    fun arreter() {
        planificateur?.shutdownNow()
        planificateur = null
    }

    // ------------------------------------------------------------------ state

    private fun installee(): Version? =
        local?.let { runCatching { it.versionDuDossier(dossier) }.getOrNull() }?.let { Version.parse(it) } ?: versionDuBuild

    override fun getEtat(): EtatMisesAJour {
        val installee = installee()
        val partagee = dossier.lireInstallation()?.let { InstallationJson.depuisJson(it) }
        return EtatMisesAJour(
            composant = local?.composant,
            installee = installee,
            canal = config.canal,
            verificationAutomatique = config.verificationAutomatique,
            derniereVerification = derniereVerification,
            erreur = erreur,
            disponibles = disponibles(installee),
            mode = mode(),
            // The other program may have installed something since: the shared file wins.
            installation = listOfNotNull(installation, partagee).maxByOrNull { it.demarreeLe },
            enAttente = enAttente,
        )
    }

    @Synchronized
    override fun verifier(): EtatMisesAJour {
        try {
            releases = github.lister()
            erreur = null
            val etat = getEtat()
            if (etat.miseAJourDisponible) {
                journal("nouvelle version disponible : ${etat.disponibles.joinToString { "${it.version} (${it.type.label.lowercase()})" }}")
            }
        } catch (e: Exception) {
            erreur = e.message ?: e.javaClass.simpleName
            journal("vérification impossible : $erreur")
        }
        derniereVerification = horloge.instant()
        return getEtat()
    }

    private fun disponibles(installee: Version?): List<VersionDisponible> {
        val prereleases = config.canal == Canal.PRERELEASE || installee?.estPrerelease == true
        val liste = releases.asSequence()
            .filter { !it.brouillon }
            .mapNotNull { r -> Version.parse(r.tag)?.let { r to it } }
            .filter { (r, v) -> prereleases || (!r.prerelease && !v.estPrerelease) }
            .filter { (_, v) -> installee == null || v > installee }
            .sortedByDescending { it.second }
            .map { (r, v) ->
                VersionDisponible(
                    version = v, type = v.typeDepuis(installee), titre = r.titre, notes = r.notes,
                    publieeLe = r.publieeLe, page = r.page, artefacts = r.artefacts,
                    migration = NotesDeVersion.migration(r.notes), incompatible = NotesDeVersion.incompatible(r.notes),
                )
            }
            .toList()
        // A development build is older than everything: the ten most recent are enough.
        return if (installee == null) liste.take(10) else liste
    }

    private fun mode(): ModeInstallation {
        val composant = local ?: return ModeInstallation(false, "Le système de mise à jour n'est pas démarré sur ce serveur.")
        if (!dossier.inscriptible()) {
            return ModeInstallation(false, "Le dossier de données ${config.dossier} n'est pas accessible en écriture (variable THERMOBOX_DATA_DIR).")
        }
        return composant.empechement()?.let { ModeInstallation(false, it) } ?: ModeInstallation(true, composant.effetBascule)
    }

    // ------------------------------------------------------------------ preparation

    override fun preparer(tag: String): Preparation {
        val installee = installee()
        val dispo = disponibles(installee).firstOrNull { it.version.tag == tag || it.version == Version.parse(tag) }
            ?: throw IllegalArgumentException("Version $tag inconnue, ou pas plus récente que la version installée")
        val v = dispo.version
        val controles = ArrayList<Controle>()

        if (dispo.type == TypeMiseAJour.CORRECTIVE) {
            controles += Controle(NiveauControle.OK, "Mise à jour corrective, recommandée : ${installee} → $v",
                "Corrections de bugs et de sécurité de la série ${v.serie}, sans nouvelle fonctionnalité : la base de données " +
                    "n'est pas modifiée, et revenir à $installee reste possible.")
        } else {
            controles += Controle(NiveauControle.AVERTISSEMENT, "Mise à jour fonctionnelle : ${installee?.serie ?: "version inconnue"} → ${v.serie}",
                "Une nouvelle série peut modifier le schéma de la base ou migrer des données. Le retour à " +
                    "${installee?.let { "${it.serie}.x" } ?: "la version actuelle"} n'est pas garanti : il peut être impossible " +
                    "sans restaurer la sauvegarde de la base faite avant l'installation.")
            controles += Controle(NiveauControle.INFO, "Schéma de la base",
                "Au démarrage, ThermoBox crée les tables manquantes mais ne modifie pas les tables existantes : " +
                    "une colonne ajoutée ou renommée demande une migration, décrite dans les notes de version.")
        }
        if (installee == null) {
            controles += Controle(NiveauControle.AVERTISSEMENT, "Version installée inconnue",
                "Ce serveur est un build de développement (${BuildInfo.texte}) : la compatibilité ne peut pas être vérifiée.")
        }
        if (v.estPrerelease) {
            controles += Controle(NiveauControle.AVERTISSEMENT, "Version de test",
                "$v est une prépublication (${v.prerelease}) : elle n'est pas considérée comme stable.")
        }
        if (installee != null && v.rangSerie - installee.rangSerie > 1) {
            val sautees = releases.mapNotNull { Version.parse(it.tag) }
                .filter { !it.estPrerelease && it.patch == 0 && it.rangSerie > installee.rangSerie && it.rangSerie < v.rangSerie }
                .sorted().joinToString()
            controles += Controle(NiveauControle.AVERTISSEMENT, "Séries mensuelles sautées",
                "De ${installee.serie} à ${v.serie} : les notes " + (if (sautees.isEmpty()) "des séries intermédiaires" else "de $sautees") +
                    " s'appliquent aussi, lisez-les.")
        }
        dispo.migration?.let { controles += Controle(NiveauControle.AVERTISSEMENT, "Opérations indiquées par les notes de version", it) }
        if (dispo.incompatible) {
            controles += Controle(NiveauControle.AVERTISSEMENT, "Les notes annoncent une incompatibilité",
                "Lisez-les avant d'installer. En cas de retour arrière, la sauvegarde de la base devra être restaurée.")
        }

        val fichiers = Artefacts.choisir(dispo.artefacts)
        val taille = fichiers?.sumOf { it.taille } ?: 0L
        when {
            fichiers == null -> controles += Controle(NiveauControle.BLOQUANT, "Fichiers de déploiement absents",
                "La version ne publie ni RestServer.jar, base.jar et le WAR, ni une archive .zip qui les contient.")
            fichiers.any { it.sha256 == null } -> controles += Controle(NiveauControle.BLOQUANT, "Empreinte SHA-256 non publiée",
                "Sans empreinte, l'intégrité des fichiers ne peut pas être garantie : installation refusée.")
            else -> controles += Controle(NiveauControle.OK, "Fichiers : ${fichiers.joinToString { it.nom }} (${mo(taille)})",
                "Leur empreinte SHA-256, publiée par GitHub, est vérifiée au téléchargement.")
        }
        controles += Controle(NiveauControle.INFO, "Java du serveur : $javaDuServeur",
            "La version de Java demandée par les fichiers est contrôlée après le téléchargement : l'installation s'arrête si elle est plus récente.")

        val libre = runCatching { dossier.preparer(); Files.getFileStore(dossier.versions).usableSpace }.getOrNull()
        if (libre != null && libre < 3 * taille + MARGE_DISQUE) {
            controles += Controle(NiveauControle.BLOQUANT, "Espace disque insuffisant",
                "${mo(libre)} libres dans ${config.dossier}, il en faut ${mo(3 * taille + MARGE_DISQUE)}.")
        }

        val mode = mode()
        if (!mode.automatique) {
            controles += Controle(NiveauControle.BLOQUANT, "Installation automatique impossible sur ce serveur", mode.detail)
        } else {
            val autre = local?.composant?.let { c -> Composant.entries.first { it != c } }
            controles += Controle(NiveauControle.INFO, "Redémarrage", mode.detail +
                (autre?.let { " L'autre composant (${it.label}) passe à $v de lui-même s'il partage le dossier ${config.dossier} ; sinon, installez la version depuis celui-ci aussi." } ?: ""))
        }
        if (installationActive()) {
            controles += Controle(NiveauControle.BLOQUANT, "Une installation est déjà en cours")
        }

        val obligatoire = dispo.type == TypeMiseAJour.FONCTIONNELLE || dispo.migration != null || dispo.incompatible || installee == null
        controles += Controle(NiveauControle.INFO, "Sauvegarde de la base de données",
            (if (obligatoire) "Faite d'office avant l'installation" else "Facultative (une corrective ne modifie pas la base), faite avant l'installation si elle est cochée") +
                ", dans ${dossier.sauvegardes}. Restauration : gunzip -c <fichier> | mysql -u <utilisateur> -p <base>.")

        return Preparation(
            version = dispo,
            installee = installee,
            controles = controles,
            sauvegardeObligatoire = obligatoire,
            retourSimple = !obligatoire,
            confirmationRequise = controles.any { it.niveau == NiveauControle.AVERTISSEMENT },
        )
    }

    /**
     * An installation runs here, or in the other program (it holds the lock). An "en cours"
     * left in the shared file by a program that stopped meanwhile is marked as interrupted.
     */
    private fun installationActive(): Boolean {
        if (enCours.get()) return true
        val i = dossier.lireInstallation()?.let { InstallationJson.depuisJson(it) } ?: return false
        if (i.etat != EtatInstallation.EN_COURS) return false
        val verrou = runCatching { dossier.verrouiller() }.getOrNull() ?: return true
        verrou.use {
            publier(i.copy(etat = EtatInstallation.ECHEC, message = "Installation interrompue (serveur arrêté pendant l'opération)",
                termineeLe = horloge.instant()))
        }
        return false
    }

    // ------------------------------------------------------------------ installation

    override fun installer(tag: String, utilisateur: String, options: OptionsInstallation): Installation {
        val preparation = preparer(tag)
        preparation.controles.firstOrNull { it.niveau == NiveauControle.BLOQUANT }?.let {
            throw IllegalStateException(listOf(it.titre, it.detail).filter { d -> d.isNotBlank() }.joinToString(" : "))
        }
        if (preparation.confirmationRequise && !options.risquesAcceptes) {
            throw IllegalArgumentException("Confirmez avoir lu les avertissements avant d'installer ${preparation.version.version}")
        }
        val composant = local ?: throw IllegalStateException("Le système de mise à jour n'est pas démarré")
        if (!enCours.compareAndSet(false, true)) throw IllegalStateException("Une installation est déjà en cours")
        val verrou = try {
            dossier.verrouiller()
        } catch (e: Exception) {
            enCours.set(false)
            throw IllegalStateException("Dossier de données inaccessible : ${e.message}")
        }
        if (verrou == null) {
            enCours.set(false)
            throw IllegalStateException("Une installation est déjà en cours depuis l'autre composant")
        }

        val sauvegarder = preparation.sauvegardeObligatoire || options.sauvegarderBase
        val depart = Installation(
            id = UUID.randomUUID().toString(),
            cible = preparation.version.version.tag,
            depuis = preparation.installee?.tag,
            type = preparation.version.type,
            composant = composant.composant,
            utilisateur = utilisateur,
            demarreeLe = horloge.instant(),
            etat = EtatInstallation.EN_COURS,
            etapes = ETAPES.map { Etape(it) } + Etape("Redémarrage : ${composant.composant.label}"),
        )
        publier(depart)
        journal("installation de ${depart.cible} lancée par $utilisateur")
        Thread({
            try {
                executer(preparation, composant, sauvegarder)
            } finally {
                verrou.close()
                enCours.set(false)
            }
        }, "thermobox-installation").apply { isDaemon = true }.start()
        return depart
    }

    private fun executer(preparation: Preparation, composant: ComposantLocal, sauvegarder: Boolean) {
        val dispo = preparation.version
        val cible = dispo.version.tag
        val temporaire = dossier.versions.resolve(".telechargement-$cible")
        try {
            etape(0) {
                if (!sauvegarder) return@etape "Non demandée" to EtatEtape.IGNOREE
                Files.createDirectories(dossier.sauvegardes)
                val fichier = dossier.sauvegardes.resolve(
                    "thermobox-${preparation.installee?.tag ?: BuildInfo.texte}-avant-$cible-${HORODATAGE.format(horloge.instant())}.sql.gz")
                val detail = sauvegarderBase(fichier)
                installation = installation?.copy(sauvegarde = fichier.toString())
                "$detail : $fichier" to EtatEtape.OK
            }
            etape(1) {
                supprimer(temporaire)
                Files.createDirectories(temporaire)
                val fichiers = Artefacts.choisir(dispo.artefacts) ?: error("Fichiers de déploiement absents")
                for (a in fichiers) {
                    var affiche = 0L
                    telechargeur.telecharger(a, temporaire.resolve(Artefacts.nomLocal(a))) { recu ->
                        if (recu - affiche >= 2 * 1024 * 1024) {
                            affiche = recu
                            detail(1, "${a.nom} : ${mo(recu)} / ${mo(a.taille)}")
                        }
                    }
                    if (a.nom.endsWith(".zip", ignoreCase = true)) {
                        val zip = temporaire.resolve(Artefacts.nomLocal(a))
                        Artefacts.extraire(zip, temporaire)
                        Files.delete(zip)
                    }
                }
                "${fichiers.joinToString { it.nom }} (${mo(fichiers.sumOf { it.taille })}), empreintes SHA-256 conformes" to EtatEtape.OK
            }
            etape(2) {
                val contenu = Artefacts.inspecter(temporaire)
                if (contenu.javaRequis > javaDuServeur) {
                    error("$cible demande Java ${contenu.javaRequis}, ce serveur tourne sous Java $javaDuServeur")
                }
                "Java ${contenu.javaRequis} requis (serveur : Java $javaDuServeur)" +
                    (if (contenu.miseAJourIntegree) "" else " ; cette version n'intègre pas le système de mise à jour : les suivantes devront être installées à la main") to EtatEtape.OK
            }
            etape(3) {
                val depuis = preparation.installee?.tag
                    ?: return@etape "Version installée inconnue : rien à conserver" to EtatEtape.IGNOREE
                conserver(composant, depuis)?.let { "Non conservée : $it" to EtatEtape.IGNOREE }
                    ?: ("Fichiers actuels (${composant.composant.label} $depuis) gardés dans ${dossier.dossier(depuis)}, pour un retour arrière manuel" to EtatEtape.OK)
            }
            etape(4) {
                dossier.sceller(temporaire, cible)
                val final = dossier.dossier(cible)
                supprimer(final)
                Files.move(temporaire, final, StandardCopyOption.ATOMIC_MOVE)
                dossier.definirCourante(cible)
                "Version courante : $cible ($final)" to EtatEtape.OK
            }
            val n = installation!!.etapes.lastIndex
            publier(installation!!.copy(etat = EtatInstallation.REDEMARRAGE, etapes = remplacer(n, EtatEtape.EN_COURS, composant.effetBascule)))
            basculer(composant, cible)
        } catch (e: Exception) {
            supprimer(temporaire)
            val message = e.message ?: e.javaClass.simpleName
            publier(installation!!.copy(etat = EtatInstallation.ECHEC, message = message, termineeLe = horloge.instant()))
            journal("installation de $cible en échec : $message")
        }
    }

    private fun etape(index: Int, bloc: () -> Pair<String, EtatEtape>) {
        publier(installation!!.copy(etapes = remplacer(index, EtatEtape.EN_COURS, "")))
        val (detail, etat) = try {
            bloc()
        } catch (e: Exception) {
            publier(installation!!.copy(etapes = remplacer(index, EtatEtape.ECHEC, e.message ?: e.javaClass.simpleName)))
            throw e
        }
        publier(installation!!.copy(etapes = remplacer(index, etat, detail)))
    }

    private fun detail(index: Int, detail: String) =
        publier(installation!!.copy(etapes = remplacer(index, EtatEtape.EN_COURS, detail)))

    private fun remplacer(index: Int, etat: EtatEtape, detail: String): List<Etape> =
        installation!!.etapes.mapIndexed { i, e -> if (i == index) e.copy(etat = etat, detail = detail) else e }

    private fun publier(i: Installation) {
        installation = i
        runCatching { dossier.ecrireInstallation(InstallationJson.versJson(i)) }
    }

    // ------------------------------------------------------------------ switch

    /**
     * Every minute: when the shared folder holds a version this program doesn't run yet
     * (installed from the other one, or here before a restart), switch to it.
     */
    internal fun suivre() {
        val composant = local ?: return
        val courante = dossier.courante()
        val enService = composant.versionDuDossier(dossier)
        cloturer(composant, enService)
        if (courante == null || courante == enService || bascule.get() || enCours.get()) {
            enAttente = null
            return
        }
        if (composant.empechement() != null || !dossier.complete(courante)) {
            enAttente = courante
            return
        }
        enAttente = null
        journal("$courante installée depuis l'autre composant : bascule de ${composant.composant.label}")
        installee()?.let { conserver(composant, it.tag) }
        basculer(composant, courante)
    }

    /** The installation this program restarted for is done once it runs the new version. */
    private fun cloturer(composant: ComposantLocal, enService: String?) {
        val i = dossier.lireInstallation()?.let { InstallationJson.depuisJson(it) } ?: return
        if (i.etat != EtatInstallation.REDEMARRAGE || i.composant != composant.composant) return
        val derniere = i.etapes.lastIndex
        when {
            enService == i.cible -> publier(i.copy(
                etat = EtatInstallation.TERMINEE, termineeLe = horloge.instant(), message = "Version ${i.cible} en service",
                etapes = i.etapes.mapIndexed { n, e -> if (n == derniere) e.copy(etat = EtatEtape.OK, detail = "Version ${i.cible} en service") else e },
            ))
            Duration.between(i.demarreeLe, horloge.instant()) > DELAI_REDEMARRAGE && !bascule.get() -> publier(i.copy(
                etat = EtatInstallation.ECHEC, termineeLe = horloge.instant(),
                message = "${composant.composant.label} tourne toujours sur ${enService ?: versionDuBuild ?: BuildInfo.texte} : redémarrage à vérifier",
                etapes = i.etapes.mapIndexed { n, e -> if (n == derniere) e.copy(etat = EtatEtape.ECHEC, detail = "Pas de redémarrage sur ${i.cible}") else e },
            ))
        }
    }

    /** Keeps the files this program runs from in updates/<depuis>; the error message, or null. */
    private fun conserver(composant: ComposantLocal, depuis: String): String? = try {
        val garde = dossier.dossier(depuis)
        Files.createDirectories(garde)
        composant.conserverActuels(garde)
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    private fun basculer(composant: ComposantLocal, tag: String) {
        if (!bascule.compareAndSet(false, true)) return
        Thread({
            try {
                Thread.sleep(delaiBascule.toMillis())
                composant.basculer(dossier.dossier(tag), tag)
            } catch (e: Exception) {
                bascule.set(false)
                val message = "Bascule vers $tag impossible : ${e.message ?: e.javaClass.simpleName}"
                journal(message)
                installation?.takeIf { it.cible == tag && it.etat == EtatInstallation.REDEMARRAGE }?.let {
                    publier(it.copy(etat = EtatInstallation.ECHEC, message = message, termineeLe = horloge.instant(),
                        etapes = it.etapes.mapIndexed { n, e2 -> if (n == it.etapes.lastIndex) e2.copy(etat = EtatEtape.ECHEC, detail = message) else e2 }))
                }
            }
        }, "thermobox-bascule").apply { isDaemon = true }.start()
    }

    private fun supprimer(dossier: Path) {
        if (!Files.exists(dossier)) return
        Files.walk(dossier).use { chemins -> chemins.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun journal(message: String) = println("[ThermoBox mises à jour] $message")

    companion object {
        private val ETAPES = listOf(
            "Sauvegarde de la base de données",
            "Téléchargement",
            "Vérification des fichiers",
            "Conservation de la version actuelle",
            "Mise en place",
        )
        private const val MARGE_DISQUE = 50L * 1024 * 1024
        private val DELAI_REDEMARRAGE: Duration = Duration.ofMinutes(10)
        private val HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        fun mo(octets: Long): String = String.format(Locale.FRANCE, "%.1f Mo", octets / 1024.0 / 1024.0)
    }
}
