package ax.ibr.thermobox.persistence.jdbc

import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.persistence.dataservices.TemperatureDataService
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime

/** Table `temperature`: setpoints and measurements (DTYPE). */
class TemperatureDataServiceJdbcImpl(db: JdbcDatabase) : TemperatureDataService, CrudJdbcService<Temperature>(db) {

    override val table = "temperature"
    override val select = "SELECT ${Rows.temperatureColumns("e")} FROM temperature e"

    override fun map(rs: ResultSet) = Rows.temperature(rs, "e")
    override fun idOf(t: Temperature) = t.id
    override fun setId(t: Temperature, id: Long) { t.id = id }

    override fun insert(cx: Connection, t: Temperature): Long = insertTemperature(cx, t)

    override fun updateRow(cx: Connection, t: Temperature) = updateTemperature(cx, t)

    /** Temperatures of exactly [temperatureClass]'s kind, the latest first. */
    override fun getByType(temperatureClass: Temperature): List<Temperature> =
        list("WHERE e.DTYPE = ? ORDER BY e.date DESC, e.id DESC", Rows.temperatureType(temperatureClass))

    override fun getAverage(temperatureClass: Temperature): Temperature? {
        val avg = db.read { cx ->
            cx.query("SELECT AVG(value) AS a FROM temperature WHERE DTYPE = ?", Rows.temperatureType(temperatureClass)) { rs ->
                rs.getDouble("a").takeUnless { rs.wasNull() }
            }.firstOrNull()
        } ?: return null
        return Temperature(avg.toFloat(), LocalDateTime.now())
    }

    internal companion object {
        fun insertTemperature(cx: Connection, t: Temperature): Long =
            cx.insert("INSERT INTO temperature (DTYPE, value, date) VALUES (?, ?, ?)", Rows.temperatureType(t), t.value, t.date)

        fun updateTemperature(cx: Connection, t: Temperature) {
            cx.execute("UPDATE temperature SET DTYPE = ?, value = ?, date = ? WHERE id = ?", Rows.temperatureType(t), t.value, t.date, t.id)
        }
    }
}
