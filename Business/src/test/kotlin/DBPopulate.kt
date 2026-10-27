import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.io.simulation.ClimatSimule
import ax.ibr.thermobox.common.entities.Batiment
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Etage
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.UserType
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Insère un jeu de données de démonstration via les services métier :
 * 3 bâtiments (dont une annexe sans étage), leurs étages et salles, plusieurs boîtiers par
 * salle, 2 boîtiers non rattachés, un utilisateur par rôle, et [HISTORIQUE_JOURS] jours de
 * mesures avec un changement de consigne au milieu.
 *
 * Relançable : un bâtiment déjà présent (même nom) est ignoré, comme les utilisateurs existants.
 * Lancer `main` avec les mêmes variables d'environnement que l'application (THERMOBOX_DB_*).
 */
object DatabaseSeeder {

    private const val HISTORIQUE_JOURS = 3L
    private const val PAS_MINUTES = 30L
    private const val MOT_DE_PASSE = "password123"

    /** [code] préfixe le nom des boîtiers, qui doit être unique (les drivers les reconnaissent par nom). */
    private class SalleSeed(val nom: String, val code: String, val consigne: Float, val boitiers: Int)
    private class EtageSeed(val nom: String, val salles: List<SalleSeed>)
    private class BatimentSeed(val nom: String, val etages: List<EtageSeed>, val salles: List<SalleSeed> = emptyList())

    private val batiments = listOf(
        BatimentSeed(
            "Bâtiment A – Siège", listOf(
                EtageSeed("A · Rez-de-chaussée", listOf(
                    SalleSeed("Accueil", "A-ACC", 21f, 2),
                    SalleSeed("Cafétéria", "A-CAF", 20.5f, 2),
                )),
                EtageSeed("A · Étage 1", listOf(
                    SalleSeed("Open space 1", "A-OS1", 21f, 3),
                    SalleSeed("Salle de réunion Lac", "A-LAC", 21f, 1),
                    SalleSeed("Bureau de direction", "A-DIR", 21.5f, 1),
                )),
                EtageSeed("A · Étage 2", listOf(
                    SalleSeed("Open space 2", "A-OS2", 21f, 3),
                    SalleSeed("Salle serveur", "A-SRV", 18f, 2),
                )),
            )
        ),
        BatimentSeed(
            "Bâtiment B – Atelier", listOf(
                EtageSeed("B · Rez-de-chaussée", listOf(
                    SalleSeed("Atelier", "B-ATL", 18f, 3),
                    SalleSeed("Stockage", "B-STO", 16f, 2),
                )),
                EtageSeed("B · Étage 1", listOf(
                    SalleSeed("Laboratoire", "B-LAB", 20f, 2),
                    SalleSeed("Salle de pause", "B-PAU", 21f, 1),
                )),
            )
        ),
        // Sans étage : les salles sont directement dans le bâtiment.
        BatimentSeed(
            "Annexe C", emptyList(), listOf(
                SalleSeed("Salle polyvalente", "C-POL", 19.5f, 2),
                SalleSeed("Vestiaires", "C-VES", 20f, 1),
            )
        ),
    )

    /** Boîtiers « tout juste branchés », à ranger dans une salle par glisser-déposer. */
    private val boitiersNonRattaches = listOf("Boîtier neuf 1", "Boîtier neuf 2")

    private val utilisateurs = listOf(
        "admin" to UserType.ADMIN,
        "gestionnaire" to UserType.GESTIONNAIRE,
        "viewer" to UserType.VIEWER,
    )

    private val salles = BusinessFactory.getSalleService()
    private val hierarchie = BusinessFactory.getHierarchieService()
    private val salleTemps = BusinessFactory.getSalleTempAttrService()
    private val users = BusinessFactory.getUserService()

    // Fixed seed: the same data on every run.
    private val random = Random(42)

    @JvmStatic
    fun main(args: Array<String>) {
        val fin = LocalDateTime.now().withSecond(0).withNano(0)
        val debut = fin.minusDays(HISTORIQUE_JOURS)

        println("== Seed : Utilisateurs ==")
        utilisateurs.forEach { (username, type) ->
            if (users.getByUsername(username) == null) {
                val user = User(username, MOT_DE_PASSE, type)
                users.add(user)
                // A new account sees nothing until it is given accesses: the demo accounts see everything.
                if (type != UserType.ADMIN) BusinessFactory.getAccesService().definir(user, false, emptyList())
                println("Utilisateur ajouté : $username ($type) / $MOT_DE_PASSE")
            } else {
                println("Utilisateur déjà existant : $username")
            }
        }

        println("== Seed : Bâtiments, étages, salles, boîtiers ==")
        val existants = salles.getAllByType(Batiment(null)).mapNotNull { it.name }.toSet()
        batiments.forEach { b ->
            if (b.nom in existants) {
                println("Bâtiment déjà existant, ignoré : ${b.nom}")
                return@forEach
            }
            val batiment = creer(Batiment(b.nom), parent = null)
            b.etages.forEach { e ->
                val etage = creer(Etage(e.nom), batiment)
                e.salles.forEach { seederSalle(it, etage, debut, fin) }
            }
            b.salles.forEach { seederSalle(it, batiment, debut, fin) }
        }

        boitiersNonRattaches.filter { salles.getByName(it) == null }.forEach {
            creer(Boitier(it), parent = null)
            println("Boîtier non rattaché : $it")
        }

        println("== Seed terminé ==")
    }

    private fun <T : Salle> creer(salle: T, parent: Salle?): T {
        salles.add(salle)
        if (parent != null) hierarchie.setParent(salle, parent)
        return salle
    }

    private fun seederSalle(seed: SalleSeed, parent: Salle, debut: LocalDateTime, fin: LocalDateTime) {
        val salle = creer(Salle(seed.nom), parent)

        // Lowered consigne at first, normal one from the second day: the curves show the change.
        val reduite = seed.consigne - 1.5f
        val changement = debut.plusDays(1)
        salleTemps.add(SalleTempAttr(salle, Consigne(reduite, debut)))
        salleTemps.add(SalleTempAttr(salle, Consigne(seed.consigne, changement)))
        val consigneA = { date: LocalDateTime -> if (date.isBefore(changement)) reduite else seed.consigne }

        for (i in 1..seed.boitiers) {
            val boitier = creer(Boitier("${seed.code}-$i"), salle)
            val nom = boitier.name.orEmpty()
            var valeur = ClimatSimule.cible(consigneA(debut), nom, debut)
            var date = debut
            while (!date.isAfter(fin)) {
                valeur = ClimatSimule.suivante(valeur, ClimatSimule.cible(consigneA(date), nom, date), random)
                salleTemps.add(SalleTempAttr(boitier, Mesurer(valeur, date)))
                date = date.plusMinutes(PAS_MINUTES)
            }
        }
        println("Salle ajoutée : ${seed.nom} (${seed.boitiers} boîtier(s), consigne ${seed.consigne} °C)")
    }
}
