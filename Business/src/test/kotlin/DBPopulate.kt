import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.entities.User
import java.sql.Date
import kotlin.random.Random

/**
 * Insère un jeu de données de test dans la base via les services métier existants.
 * À exécuter une seule fois (ou plusieurs fois si tu veux ajouter des données,
 * mais attention aux doublons de noms de salle / username).
 */
object DatabaseSeeder {

    private val salleNames = listOf("Salle A", "Salle B", "Salle C", "Salle Serveur", "Salle Réunion")
    private val usernames = listOf("admin", "technicien", "invite")

    @JvmStatic
    fun main(args: Array<String>) {
        val factory = BusinessFactory()

        val salleService = factory.getSalleService()
        val temperatureService = factory.getTemperatureService()
        val salleTempAttrService = factory.getSalleTempAttrService()
        val userService = factory.getUserService()

        println("== Seed : Salles ==")
        val salles = salleNames.map { name ->
            val existing = salleService.getByName(name)
            if (existing != null) {
                println("Salle déjà existante : $name")
                existing
            } else {
                val salle = Salle(name)
                salleService.add(salle)
                println("Salle ajoutée : $name")
                salleService.getByName(name) ?: salle
            }
        }

        println("== Seed : Utilisateurs ==")
        usernames.forEach { username ->
            if (userService.getByUsername(username) == null) {
                userService.add(User(username, "password123"))
                println("Utilisateur ajouté : $username")
            } else {
                println("Utilisateur déjà existant : $username")
            }
        }

        println("== Seed : Températures + SalleTempAttr ==")
        salles.forEach { salle ->
            repeat(3) {
                val value = Random.nextFloat() * (28f - 15f) + 15f
                val temperature = Temperature(value, Date(System.currentTimeMillis()))
                temperatureService.add(temperature)
                salleTempAttrService.add(SalleTempAttr(salle, temperature))
                println("Température ajoutée pour ${salle.name} : %.2f°C".format(value))
            }
        }

        println("== Seed terminé ==")
    }
}