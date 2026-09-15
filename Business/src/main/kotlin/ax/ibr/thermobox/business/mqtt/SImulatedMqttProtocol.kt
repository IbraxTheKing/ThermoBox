package ax.ibr.thermobox.business.mqtt

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.SalleService
import ax.ibr.thermobox.common.services.SalleTempAttrService
import ax.ibr.thermobox.common.services.TemperatureService
import java.sql.Date
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Implémentation factice de ProtocolDriver, utilisée pour tester la couche
 * persistence sans avoir besoin d'un vrai broker MQTT.
 *
 * Elle simule périodiquement :
 * - l'apparition de nouvelles salles (comme si un device s'annonçait sur TOPIC_SALLES)
 * - l'arrivée de mesures de température pour des salles existantes
 *
 * Utilisation : instancier à la place de MqttSendReceiver dans ton point d'entrée
 * (ou dans BusinessFactory) le temps de tester la BDD.
 */
class SimulatedProtocolDriver(
    private val simulatedSalleNames: List<String> = listOf("Salle A", "Salle B", "Salle C"),
    private val temperatureIntervalMs: Long = 3000L,
    private val salleDiscoveryIntervalMs: Long = 10000L,
    private val minTemp: Float = 15f,
    private val maxTemp: Float = 28f
) : ProtocolDriver() {

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var running = false

    override fun listen() {
        if (running) return
        running = true
        listenToRooms()
        listenToTemperatures()
        println("[SimulatedProtocolDriver] Simulation démarrée (pas de broker MQTT requis).")
    }

    override fun listenToRooms() {
        // Simule la découverte périodique de salles, comme si elles s'annonçaient sur TOPIC_SALLES
        scheduler.scheduleAtFixedRate({
            try {
                val name = simulatedSalleNames.random()
                if (salleService.getByName(name) == null) {
                    salleService.update(Salle(name))
                    println("[SimulatedProtocolDriver] Nouvelle salle simulée : $name")
                }
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] Erreur listenToRooms : ${e.message}")
            }
        }, 0, salleDiscoveryIntervalMs, TimeUnit.MILLISECONDS)
    }

    override fun listenToTemperatures() {
        // Simule l'arrivée régulière de mesures de température pour des salles existantes
        scheduler.scheduleAtFixedRate({
            try {
                val salles = salleService.getAll()
                if (salles.isEmpty()) return@scheduleAtFixedRate

                val salle = salles.random()
                val value = Random.nextFloat() * (maxTemp - minTemp) + minTemp
                val temperature = Mesurer(value, LocalDateTime.now())

                temperatureService.update(temperature)
                salleTempAttrService.update(SalleTempAttr(salle, temperature))

                println("[SimulatedProtocolDriver] Température simulée pour ${salle.name} : %.2f°C".format(value))
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] Erreur listenToTemperatures : ${e.message}")
            }
        }, salleDiscoveryIntervalMs / 2, temperatureIntervalMs, TimeUnit.MILLISECONDS)
    }

    override fun sendConsigne(s: Salle, c: Consigne) {
        // Pas de broker à publier : on log simplement l'action pour vérifier le flux
        println("[SimulatedProtocolDriver] Consigne simulée envoyée à ${s?.name ?: "salle inconnue"} : ${c.value}")
        temperatureService.add(c)
        salleTempAttrService.add(SalleTempAttr(s,c))

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