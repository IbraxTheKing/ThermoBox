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
import ax.ibr.thermobox.common.services.ConsigneEffective
import ax.ibr.thermobox.common.services.EnvoiConsigne
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

    override fun getConsigneEffective(salle: Salle): ConsigneEffective? {
        val propre = salleTemps.getConsigneFromSalle(salle)
        // Nearest ancestor first: an étage's consigne wins over its bâtiment's.
        var heritee: ConsigneEffective? = null
        for (ancetre in hierarchie.getChemin(salle).asReversed()) {
            val c = salleTemps.getConsigneFromSalle(ancetre) ?: continue
            heritee = ConsigneEffective(c, ancetre, heritee = true)
            break
        }
        return when {
            propre == null -> heritee
            // Set by a forced consigne of the ancestor: the salle still follows it.
            heritee != null && alignee(propre, heritee.consigne) -> heritee
            else -> ConsigneEffective(propre, salle, heritee = false)
        }
    }

    // A box's own consigne is only the last one it received: it never counts as a choice to keep.
    override fun getConsignesPropres(salle: Salle): List<ConsigneEffective> {
        val propres = ArrayList<ConsigneEffective>()
        fun parcourir(s: Salle, suivie: Consigne?, profondeur: Int) {
            if (profondeur > MAX_DEPTH) return
            for (enfant in hierarchie.getChildren(s)) {
                if (enfant is Boitier) continue
                val propre = salleTemps.getConsigneFromSalle(enfant)
                val distincte = propre != null && !alignee(propre, suivie)
                if (distincte) propres.add(ConsigneEffective(propre!!, enfant, heritee = false))
                parcourir(enfant, if (distincte) propre else suivie, profondeur + 1)
            }
        }
        parcourir(salle, getConsigne(salle), 0)
        return propres
    }

    override fun getHistoriques(salle: Salle, start: Instant, end: Instant): List<HistoriqueBoitier> =
        hierarchie.getBoitiers(salle).map { boitier ->
            HistoriqueBoitier(boitier, salleTemps.getTemperaturesFromSalleTimed(boitier, start, end))
        }

    override fun getMoyenneHistorique(salle: Salle, start: Instant, end: Instant): List<Temperature> =
        moyenneGlissante(getHistoriques(salle, start, end))

    override fun getConsignes(salle: Salle, start: Instant, end: Instant): List<Consigne> {
        val source = getConsigneEffective(salle)?.source ?: salle
        return salleTemps.getConsignesFromSalleTimed(source, start, end)
    }

    override fun envoyerConsigne(salle: Salle, valeur: Float, forcer: Boolean): EnvoiConsigne {
        ConsigneRules.check(valeur)
        val precedente = getConsigne(salle)
        val date = LocalDateTime.now()
        salleTemps.add(SalleTempAttr(salle, Consigne(valeur, date)))

        val conservees = ArrayList<ConsigneEffective>()
        val remplacees = ArrayList<Salle>()
        val boitiers = ArrayList<Boitier>()
        // suivie: the consigne the current element followed before this one.
        fun parcourir(s: Salle, suivie: Consigne?, profondeur: Int) {
            if (s is Boitier) {
                boitiers.add(s)
                return
            }
            if (profondeur > MAX_DEPTH) return
            for (enfant in hierarchie.getChildren(s)) {
                val propre = if (enfant is Boitier) null else salleTemps.getConsigneFromSalle(enfant)
                if (propre != null) {
                    val distincte = !alignee(propre, suivie)
                    if (distincte && !forcer) {
                        // It keeps its consigne, and so do its boxes.
                        conservees.add(ConsigneEffective(propre, enfant, heritee = false))
                        continue
                    }
                    // Same consigne and date as the parent: it keeps following it from now on.
                    salleTemps.add(SalleTempAttr(enfant, Consigne(valeur, date)))
                    if (distincte) remplacees.add(enfant)
                    parcourir(enfant, if (distincte) propre else suivie, profondeur + 1)
                    continue
                }
                parcourir(enfant, suivie, profondeur + 1)
            }
        }
        parcourir(salle, precedente, 0)

        // A box that fails must not prevent the others from receiving the consigne.
        val echecs = boitiers.filter { boitier ->
            runCatching { driver().sendConsigne(boitier, Consigne(valeur, date)) }.isFailure
        }
        return EnvoiConsigne(conservees, remplacees, boitiers, echecs)
    }

    /**
     * A consigne saved by a forced send carries the value and the date of its ancestor's: the
     * element then still follows the ancestor rather than having a consigne of its own.
     */
    private fun alignee(propre: Consigne, suivie: Consigne?): Boolean =
        suivie != null && propre.date == suivie.date && propre.value == suivie.value

    companion object {
        /** Deepest chain: Batiment → Etage → Salle → Boitier. */
        private const val MAX_DEPTH = 4

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
