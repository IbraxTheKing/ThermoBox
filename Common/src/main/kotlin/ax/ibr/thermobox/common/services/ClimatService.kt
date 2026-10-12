package ax.ibr.thermobox.common.services

import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Consigne
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.Temperature
import java.io.Serializable
import java.time.Instant

/** Latest measure of a box, null if it never reported. */
data class MesureBoitier(val boitier: Boitier, val mesure: Temperature?) : Serializable

/** Measures of a box over a period, oldest first. */
data class HistoriqueBoitier(val boitier: Boitier, val mesures: List<Temperature>) : Serializable

/**
 * The consigne that applies to an element, and the element it was set on ([source]): the
 * element itself, or its nearest ancestor that has one ([heritee]).
 */
data class ConsigneEffective(val consigne: Consigne, val source: Salle, val heritee: Boolean) : Serializable

/** Outcome of [ClimatService.envoyerConsigne]. */
data class EnvoiConsigne(
    /** Étages and salles under the target that kept their own consigne (not forced). */
    val conservees: List<ConsigneEffective>,
    /** Étages and salles under the target whose own consigne was replaced (forced). */
    val remplacees: List<Salle>,
    /** Boxes the consigne was sent to. */
    val boitiers: List<Boitier>,
    /** Boxes it could not be sent to. */
    val echecs: List<Boitier>,
) : Serializable

/**
 * Temperatures seen at the level of any element of the hierarchy: a salle (or an étage, a
 * bâtiment) aggregates the boxes it contains, at any depth.
 */
interface ClimatService {

    /** Latest measure of every box under [salle]. */
    fun getMesures(salle: Salle): List<MesureBoitier>

    /** Average of the latest measure of every box under [salle], null if none has reported. */
    fun getMoyenne(salle: Salle): Float?

    /** Consigne of [salle], or else of its nearest ancestor (a salle follows its bâtiment's). */
    fun getConsigne(salle: Salle): Consigne? = getConsigneEffective(salle)?.consigne

    fun getConsigneEffective(salle: Salle): ConsigneEffective?

    /**
     * Étages and salles under [salle], at any depth, that have their own consigne: a consigne
     * sent to [salle] leaves them alone unless forced. Boxes never count.
     */
    fun getConsignesPropres(salle: Salle): List<ConsigneEffective>

    fun getHistoriques(salle: Salle, start: Instant, end: Instant): List<HistoriqueBoitier>

    /**
     * Average of the boxes over time: one point per measure, each box counting with its last
     * known value at that moment (boxes don't report at the same time).
     */
    fun getMoyenneHistorique(salle: Salle, start: Instant, end: Instant): List<Temperature>

    /** Consignes over the period, taken where the current one comes from (inherited or not). */
    fun getConsignes(salle: Salle, start: Instant, end: Instant): List<Consigne>

    /**
     * Saves [valeur] as the consigne of [salle], which the étages and salles under it without a
     * consigne of their own then follow, and sends it to their boxes. Those with their own
     * consigne keep it, with their boxes, unless [forcer]: then [valeur] replaces it too.
     *
     * @throws Exception if the value is rejected (too hot, impossible); nothing is saved then.
     */
    fun envoyerConsigne(salle: Salle, valeur: Float, forcer: Boolean = false): EnvoiConsigne
}
