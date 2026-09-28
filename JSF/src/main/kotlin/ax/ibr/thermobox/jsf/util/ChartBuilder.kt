package ax.ibr.thermobox.jsf.util

import ax.ibr.thermobox.common.entities.Temperature
import org.primefaces.model.charts.ChartData
import org.primefaces.model.charts.line.LineChartDataSet
import org.primefaces.model.charts.line.LineChartModel
import java.time.format.DateTimeFormatter

/** Construit le graphique d'historique (p:lineChart) à partir de plusieurs séries de températures. */
object ChartBuilder {
    private val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    data class Serie(val label: String, val points: List<Temperature>, val color: String)

    fun build(series: List<Serie>): LineChartModel {
        // Axe X commun à toutes les séries
        val dates = series.flatMap { s -> s.points.mapNotNull { it.date } }.distinct().sorted()

        val chartData = ChartData()
        chartData.labels = dates.map { fmt.format(it) }

        series.filter { it.points.isNotEmpty() }.forEach { serie ->
            val byDate = serie.points.associate { it.date to it.value }
            // On reporte la dernière valeur connue : une consigne reste valable jusqu'à la suivante
            var last: Float? = null
            val values = ArrayList<Any?>()
            for (d in dates) {
                byDate[d]?.let { last = it }
                values.add(last)
            }
            val dataSet = LineChartDataSet()
            dataSet.label = serie.label
            dataSet.borderColor = serie.color
            dataSet.backgroundColor = serie.color
            dataSet.fill = false
            dataSet.data = values
            chartData.addChartDataSet(dataSet)
        }

        return LineChartModel().apply { data = chartData }
    }
}
