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
 * Temperatures seen at the level of any element of the hierarchy: a salle (or an étage, a
 * bâtiment) aggregates the boxes it contains, at any depth.
 */
interface ClimatService {

    /** Latest measure of every box under [salle]. */
    fun getMesures(salle: Salle): List<MesureBoitier>

    /** Average of the latest measure of every box under [salle], null if none has reported. */
    fun getMoyenne(salle: Salle): Float?

    fun getConsigne(salle: Salle): Consigne?

    fun getHistoriques(salle: Salle, start: Instant, end: Instant): List<HistoriqueBoitier>

    /**
     * Average of the boxes over time: one point per measure, each box counting with its last
     * known value at that moment (boxes don't report at the same time).
     */
    fun getMoyenneHistorique(salle: Salle, start: Instant, end: Instant): List<Temperature>

    fun getConsignes(salle: Salle, start: Instant, end: Instant): List<Consigne>

    /**
     * Saves [valeur] as the consigne of [salle], then sends it to every box under it.
     *
     * @return the boxes the consigne could not be sent to (empty when all succeeded).
     * @throws Exception if the value is rejected (too hot, impossible); nothing is saved then.
     */
    fun envoyerConsigne(salle: Salle, valeur: Float): List<Boitier>
}
