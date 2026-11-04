package ax.ibr.thermobox.persistence.dataservices

import ax.ibr.thermobox.common.alerts.AlertChannel
import ax.ibr.thermobox.common.alerts.AlertDelivery
import ax.ibr.thermobox.common.alerts.AlertEvent
import ax.ibr.thermobox.common.alerts.AlertRule
import ax.ibr.thermobox.common.alerts.AlertState
import java.time.LocalDateTime

/**
 * Storage of the temperature alerts (tables alert_*, migration V9): channels, rules, the history
 * of the excesses and of the messages sent about them.
 *
 * Plain JDBC whatever THERMOBOX_DB_ACCESS says (see PersistenceFactory.getAlertDataService): the
 * alerts came after JDBC became the default, and their rows are not entities of persistence.xml.
 */
interface AlertDataService {

    // --- channels

    fun channels(): List<AlertChannel>
    fun channel(id: Long): AlertChannel?
    /** Added (no id) or replaced; the saved channel, with its id and dates. */
    fun save(channel: AlertChannel): AlertChannel
    /** Also takes it out of the rules; its deliveries stay, without it. */
    fun deleteChannel(id: Long): Boolean

    // --- rules

    fun rules(): List<AlertRule>
    fun rule(id: Long): AlertRule?
    /** Added (no id) or replaced, with its scope and channels. */
    fun save(rule: AlertRule): AlertRule
    /** Its events stay in the history, without it. */
    fun deleteRule(id: Long): Boolean

    // --- events

    fun events(state: AlertState? = null, roomId: Long? = null, ruleId: Long? = null, before: Long? = null, limit: Int = 100): List<AlertEvent>
    fun event(id: Long): AlertEvent?
    fun activeEvents(): List<AlertEvent>
    /** Added (no id) or updated. */
    fun save(event: AlertEvent): AlertEvent

    // --- deliveries

    fun deliveries(eventId: Long): List<AlertDelivery>
    fun deliveries(status: ax.ibr.thermobox.common.alerts.DeliveryStatus?, limit: Int = 100): List<AlertDelivery>
    /** PENDING ones whose time has come (next attempt null or past [now]), oldest first. */
    fun dueDeliveries(now: LocalDateTime, limit: Int = 100): List<AlertDelivery>
    fun save(delivery: AlertDelivery): AlertDelivery

    /** Events (and their deliveries) that ended before [date], and tests older than it. */
    fun purge(before: LocalDateTime): Int

    /**
     * Runs [block] if no other server runs it at the same time (MySQL GET_LOCK on [name]), and
     * returns its result; null without running it when another one holds the lock.
     */
    fun <T> exclusively(name: String, block: () -> T): T?
}
