package ax.ibr.thermobox.business.io.mqtt

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.business.io.simulation.SimulatedClimate
import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.common.entities.Device
import ax.ibr.thermobox.common.entities.Setpoint
import ax.ibr.thermobox.common.entities.Measurement
import ax.ibr.thermobox.common.entities.Room
import ax.ibr.thermobox.common.entities.RoomTempAttr
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Fake ProtocolDriver, to work without an MQTT broker or real devices.
 *
 * - It measures **every** device in the database (the DatabaseSeeder's included). Each device
 *   tends towards the setpoint of its room (see [SimulatedClimate]): a setpoint changed in a client
 *   shows on the curves as measurements come in.
 * - It announces the devices of [newDevices] one by one, like hardware that was just plugged in:
 *   they arrive unattached, to be put in their room.
 */
class SimulatedProtocolDriver(
    private val newDevices: List<String> = listOf("New device 3", "New device 4", "New device 5"),
    private val temperatureIntervalMs: Long = 60_000L,
    private val discoveryIntervalMs: Long = 120_000L,
) : ProtocolDriver() {

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var running = false

    private val hierarchy by lazy { BusinessFactory.getHierarchyService() }
    private val climate by lazy { BusinessFactory.getClimateService() }

    /** Last simulated value per box id, so readings drift smoothly instead of jumping. */
    private val latest = ConcurrentHashMap<Long, Float>()

    override fun listen() {
        if (running) return
        running = true
        listenToRooms()
        listenToTemperatures()
        println("[SimulatedProtocolDriver] Simulation started (no MQTT broker needed).")
    }

    override fun listenToRooms() {
        scheduler.scheduleAtFixedRate({
            try {
                // One new box per tick; registerDevice skips names already in database.
                val name = newDevices.firstOrNull { deviceName -> getDevices().none { it.name == deviceName } }
                if (name != null && registerDevice(name)) {
                    println("[SimulatedProtocolDriver] New simulated device: $name")
                }
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] listenToRooms error: ${e.message}")
            }
        }, discoveryIntervalMs, discoveryIntervalMs, TimeUnit.MILLISECONDS)
    }

    override fun listenToTemperatures() {
        scheduler.scheduleAtFixedRate({
            try {
                val devices = getDevices()
                val now = LocalDateTime.now()
                devices.forEach { device -> measure(device, now) }
                if (devices.isNotEmpty()) {
                    println("[SimulatedProtocolDriver] ${devices.size} simulated measurement(s)")
                }
            } catch (e: Exception) {
                println("[SimulatedProtocolDriver] listenToTemperatures error: ${e.message}")
            }
        }, 0, temperatureIntervalMs, TimeUnit.MILLISECONDS)
    }

    private fun measure(device: Device, date: LocalDateTime) {
        val id = device.id ?: return
        val deviceName = device.name.orEmpty()
        val target = SimulatedClimate.target(setpoint(device), deviceName, date)
        val current = latest[id]
            ?: roomTempAttrService.getCurrentTemperatureFromRoom(device, Measurement(0f, date))?.value
            ?: target
        val value = SimulatedClimate.next(current, target)
        latest[id] = value
        // The link cascades to the temperature: saving it separately would insert it twice.
        roomTempAttrService.add(RoomTempAttr(device, Measurement(value, date)))
    }

    /** Latest setpoint the device knows: its own (sent by sendSetpoint) or the one its room follows. */
    private fun setpoint(device: Device): Float {
        val room = hierarchy.getParent(device)
        return listOfNotNull(
            roomTempAttrService.getSetpointFromRoom(device),
            room?.let { climate.getSetpoint(it) }
        ).maxByOrNull { it.date ?: LocalDateTime.MIN }?.value ?: SimulatedClimate.DEFAULT_SETPOINT
    }

    override fun sendSetpoint(s: Room, c: Setpoint) {
        super.sendSetpoint(s, c)
        println("[SimulatedProtocolDriver] Simulated setpoint sent to ${s.name} : ${c.value}")
        // A real box would apply it; here we keep it on the box so the simulation follows it.
        roomTempAttrService.add(RoomTempAttr(s, c))
    }

    /** Stops the scheduled tasks cleanly. */
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
        println("[SimulatedProtocolDriver] Simulation stopped.")
    }
}
