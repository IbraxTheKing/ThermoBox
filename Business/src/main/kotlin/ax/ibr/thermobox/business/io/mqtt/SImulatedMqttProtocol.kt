package ax.ibr.thermobox.business.io.mqtt

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.io.simulation.ClimatSimule
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Implémentation factice de ProtocolDriver, pour travailler sans broker MQTT ni vrais boîtiers.
 *
 * - Elle mesure **tous** les boîtiers en base (ceux du DatabaseSeeder compris). Chaque boîtier
 *   tend vers la consigne de sa salle (voir [ClimatSimule]) : une consigne changée dans JSF se
 *   voit sur les courbes au fil des mesures.
 * - Elle annonce un à un les boîtiers de [nouveauxBoitiers], comme des appareils qu'on vient de
 *   brancher : ils arrivent non rattachés, à ranger dans leur salle.
 */
class SimulatedProtocolDriver(
    private val nouveauxBoitiers: List<String> = listOf("Boîtier neuf 3", "Boîtier neuf 4", "Boîtier neuf 5"),
    private val temperatureIntervalMs: Long = 60_000L,
    private val discoveryIntervalMs: Long = 120_000L,
) : ProtocolDriver() {

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var running = false

    private val hierarchie by lazy { BusinessFactory.getHierarchieService() }
    private val climat by lazy { BusinessFactory.getClimatService() }

    /** Last simulated value per box id, so readings drift smoothly instead of jumping. */
    private val dernieres = ConcurrentHashMap<Long, Float>()

    override fun listen() {
        if (running) return
        running = true
        listenToRooms()
        listenToTemperatures()
        println("[SimulatedProtocolDriver] Simulation démarrée (pas de broker MQTT requis).")
    }

    override fun listenToRooms() {
        scheduler.scheduleAtFixedRate({
            try {
                // One new box per tick; registerBoitier skips names already in database.
                val name = nouveauxBoitiers.firstOrNull { nom -> getBoitiers().none { it.name == nom } }
                if (name != null && registerBoitier(name)) {
                    println("[SimulatedProtocolDriver] Nouveau boîtier simulé : $name")
                }
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] Erreur listenToRooms : ${e.message}")
            }
        }, discoveryIntervalMs, discoveryIntervalMs, TimeUnit.MILLISECONDS)
    }

    override fun listenToTemperatures() {
        scheduler.scheduleAtFixedRate({
            try {
                val boitiers = getBoitiers()
                val maintenant = LocalDateTime.now()
                boitiers.forEach { boitier -> mesurer(boitier, maintenant) }
                if (boitiers.isNotEmpty()) {
                    println("[SimulatedProtocolDriver] ${boitiers.size} mesure(s) simulée(s)")
                }
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] Erreur listenToTemperatures : ${e.message}")
            }
        }, 0, temperatureIntervalMs, TimeUnit.MILLISECONDS)
    }

    private fun mesurer(boitier: Boitier, date: LocalDateTime) {
        val id = boitier.id ?: return
        val nom = boitier.name.orEmpty()
        val cible = ClimatSimule.cible(consigne(boitier), nom, date)
        val actuelle = dernieres[id]
            ?: salleTempAttrService.getCurrentTemperatureFromSalle(boitier, Mesurer(0f, date))?.value
            ?: cible
        val valeur = ClimatSimule.suivante(actuelle, cible)
        dernieres[id] = valeur
        // The link cascades to the temperature: saving it separately would insert it twice.
        salleTempAttrService.add(SalleTempAttr(boitier, Mesurer(valeur, date)))
    }

    /** Latest consigne the box knows: its own (sent by sendConsigne) or the one its salle follows. */
    private fun consigne(boitier: Boitier): Float {
        val salle = hierarchie.getParent(boitier)
        return listOfNotNull(
            salleTempAttrService.getConsigneFromSalle(boitier),
            salle?.let { climat.getConsigne(it) }
        ).maxByOrNull { it.date ?: LocalDateTime.MIN }?.value ?: ClimatSimule.CONSIGNE_PAR_DEFAUT
    }

    override fun sendConsigne(s: Salle, c: Consigne) {
        super.sendConsigne(s, c)
        println("[SimulatedProtocolDriver] Consigne simulée envoyée à ${s.name} : ${c.value}")
        // A real box would apply it; here we keep it on the box so the simulation follows it.
        salleTempAttrService.add(SalleTempAttr(s, c))
    }

    /** Arrête proprement les tâches planifiées. */
    fun stop() {
        running = false
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
        println("[SimulatedProtocolDriver] Simulation arrêtée.")
    }
}
