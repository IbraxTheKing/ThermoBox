package ax.ibr.thermobox.business.alerts

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import ax.ibr.thermobox.common.alerts.DeliveryStatus
import ax.ibr.thermobox.persistence.dataservices.AlertDataService
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/** [AlertDataService] in memory, for the tests. */
class FakeAlertStore : AlertDataService {
    private val ids = AtomicLong()
    val channels = LinkedHashMap<Long, AlertChannel>()
    val rules = LinkedHashMap<Long, AlertRule>()
    val events = LinkedHashMap<Long, AlertEvent>()
    val deliveries = LinkedHashMap<Long, AlertDelivery>()
    var lockedElsewhere = false

    override fun channels() = channels.values.toList()
    override fun channel(id: Long) = channels[id]
    override fun save(channel: AlertChannel) = (channel.id?.let { channel } ?: channel.copy(id = ids.incrementAndGet())).also { channels[it.id!!] = it }
    override fun deleteChannel(id: Long): Boolean {
        rules.replaceAll { _, r -> r.copy(channels = r.channels - id) }
        return channels.remove(id) != null
    }

    override fun rules() = rules.values.toList()
    override fun rule(id: Long) = rules[id]
    override fun save(rule: AlertRule) = (rule.id?.let { rule } ?: rule.copy(id = ids.incrementAndGet())).also { rules[it.id!!] = it }
    override fun deleteRule(id: Long): Boolean {
        events.replaceAll { _, e -> if (e.ruleId == id) e.copy(ruleId = null) else e }
        return rules.remove(id) != null
    }

    override fun events(state: AlertState?, roomId: Long?, ruleId: Long?, before: Long?, limit: Int) =
        events.values.filter { (state == null || it.state == state) && (roomId == null || it.roomId == roomId) && (ruleId == null || it.ruleId == ruleId) && (before == null || it.id!! < before) }
            .sortedByDescending { it.id }.take(limit).map { withFailures(it) }

    override fun event(id: Long) = events[id]?.let { withFailures(it) }
    override fun activeEvents() = events(AlertState.ACTIVE, null, null, null, 1000)
    override fun save(event: AlertEvent) = (event.id?.let { event } ?: event.copy(id = ids.incrementAndGet())).also { events[it.id!!] = it }

    private fun withFailures(e: AlertEvent) = e.copy(deliveryFailed = deliveries.values.any { it.eventId == e.id && it.status == DeliveryStatus.FAILED })

    override fun deliveries(eventId: Long) = deliveries.values.filter { it.eventId == eventId }
    override fun deliveries(status: DeliveryStatus?, limit: Int) = deliveries.values.filter { status == null || it.status == status }.reversed().take(limit)
    override fun dueDeliveries(now: LocalDateTime, limit: Int) =
        deliveries.values.filter { it.status == DeliveryStatus.PENDING && (it.nextAttemptAt == null || !it.nextAttemptAt!!.isAfter(now)) }.take(limit)
    override fun save(delivery: AlertDelivery) = (delivery.id?.let { delivery } ?: delivery.copy(id = ids.incrementAndGet())).also { deliveries[it.id!!] = it }

    override fun purge(before: LocalDateTime): Int {
        val old = events.values.filter { it.state != AlertState.ACTIVE && it.resolvedAt!!.isBefore(before) }.map { it.id!! }
        old.forEach { id -> events.remove(id); deliveries.values.removeIf { it.eventId == id } }
        return old.size
    }

    override fun <T> exclusively(name: String, block: () -> T): T? = if (lockedElsewhere) null else block()
}
