package ax.ibr.thermobox.persistence

/**
 * Table `latest_measurement` (migration V5): for each device, its latest measurement. Finding it
 * among the history means reading every measurement of the device; this table answers in one
 * lookup, for all the devices at once if needed.
 *
 * Both implementations (JPA and JDBC) keep it up to date with these statements:
 * - a measurement linked to a device ([UPSERT]): it becomes the latest if it is not older;
 * - a measurement changed, moved or deleted ([FORGET_TEMPERATURE], [FORGET_ROOM],
 *   [FORGET_ROOMS_OF_TEMPERATURE]): the row goes, and the next read works the latest out again
 *   from the history and puts it back. So a row is never wrong, at worst missing.
 */
object LatestMeasurements {

    /** Parameters: room id, temperature id, date. Keeps the newer of the two (an equal date: the new one). */
    const val UPSERT =
        "INSERT INTO latest_measurement (room_id, temperature_id, date) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "temperature_id = IF(date IS NULL OR VALUES(date) >= date, VALUES(temperature_id), temperature_id), " +
            "date = IF(date IS NULL OR VALUES(date) >= date, VALUES(date), date)"

    /** Parameter: temperature id. */
    const val FORGET_TEMPERATURE = "DELETE FROM latest_measurement WHERE temperature_id = ?"

    /** Parameter: room id. */
    const val FORGET_ROOM = "DELETE FROM latest_measurement WHERE room_id = ?"

    /** Parameter: temperature id. The device it belongs to works its latest out again. */
    const val FORGET_ROOMS_OF_TEMPERATURE =
        "DELETE FROM latest_measurement WHERE room_id IN (SELECT room_id FROM room_temperature_attr WHERE temperature_id = ?)"

    /** IN lists are cut in parts of this size. */
    const val CHUNK = 1000
}
