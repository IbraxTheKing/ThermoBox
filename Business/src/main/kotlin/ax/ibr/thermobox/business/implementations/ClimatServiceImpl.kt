package ax.ibr.thermobox.business.implementations

import ax.ibr.thermobox.business.protocols.ProtocolDriver
import ax.ibr.thermobox.business.rules.ConsigneRules
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Mesurer
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleTempAttr
import ax.ibr.thermobox.common.entities.Temperature
import ax.ibr.thermobox.common.services.ClimatService
import ax.ibr.thermobox.common.services.HierarchieService
import ax.ibr.thermobox.common.services.HistoriqueBoitier
import ax.ibr.thermobox.common.services.MesureBoitier
import ax.ibr.thermobox.common.services.SalleTempAttrService
import java.io.Serializable
import java.time.Instant
import java.time.LocalDateTime

class ClimatServiceImpl(
    private val hierarchie: HierarchieService = BusinessFactory.getHierarchieService(),
    private val salleTemps: SalleTempAttrService = BusinessFactory.getSalleTempAttrService(),
    private val driver: () -> ProtocolDriver = { BusinessFactory.getDriver() }
) : ClimatService, Serializable {

    override fun getMesures(salle: Salle): List<MesureBoitier> =
        hierarchie.getBoitiers(salle).map { boitier ->
            MesureBoitier(boitier, salleTemps.getCurrentTemperatureFromSalle(boitier, Mesurer(0f, LocalDateTime.now())))
        }

    override fun getMoyenne(salle: Salle): Float? =
        getMesures(salle).mapNotNull { it.mesure?.value }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    override fun getConsigne(salle: Salle): Consigne? = salleTemps.getConsigneFromSalle(salle)

    override fun getHistoriques(salle: Salle, start: Instant, end: Instant): List<HistoriqueBoitier> =
        hierarchie.getBoitiers(salle).map { boitier ->
            HistoriqueBoitier(boitier, salleTemps.getTemperaturesFromSalleTimed(boitier, start, end))
        }

    override fun getMoyenneHistorique(salle: Salle, start: Instant, end: Instant): List<Temperature> =
        moyenneGlissante(getHistoriques(salle, start, end))

    override fun getConsignes(salle: Salle, start: Instant, end: Instant): List<Consigne> =
        salleTemps.getConsignesFromSalleTimed(salle, start, end)

    override fun envoyerConsigne(salle: Salle, valeur: Float): List<Boitier> {
        ConsigneRules.check(valeur)
        val date = LocalDateTime.now()
        salleTemps.add(SalleTempAttr(salle, Consigne(valeur, date)))

        // A box that fails must not prevent the others from receiving the consigne.
        return hierarchie.getBoitiers(salle).filter { boitier ->
            runCatching { driver().sendConsigne(boitier, Consigne(valeur, date)) }.isFailure
        }
    }

    companion object {
        /**
         * Average of several series over time. At each timestamp of any series, every series
         * counts with its last value so far; series that haven't started yet are left out.
         */
        fun moyenneGlissante(historiques: List<HistoriqueBoitier>): List<Temperature> {
            val dates = historiques.flatMap { h -> h.mesures.mapNotNull { it.date } }.distinct().sorted()
            val series = historiques.map { h -> h.mesures.filter { it.date != null && it.value != null } }
            val positions = IntArray(series.size)
            val dernieres = arrayOfNulls<Float>(series.size)

            return dates.map { date ->
                series.forEachIndexed { i, mesures ->
                    while (positions[i] < mesures.size && !mesures[positions[i]].date!!.isAfter(date)) {
                        dernieres[i] = mesures[positions[i]].value
                        positions[i]++
                    }
                }
                Temperature(dernieres.filterNotNull().average().toFloat(), date)
            }
        }
    }
}
