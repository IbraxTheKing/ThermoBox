package ax.ibr.thermobox.business.io.simulation

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plausible temperatures for fake boxes, shared by the simulated driver and the database seeder
 * so the seeded history and the live readings look alike.
 *
 * A box tends towards its consigne, shifted by its spot in the room and by the time of day.
 */
object ClimatSimule {

    const val CONSIGNE_PAR_DEFAUT = 20f

    /** Stable offset of a box from its room (near a window, a radiator…): -0.8 to +0.8 °C. */
    fun decalage(nomBoitier: String): Float = (nomBoitier.hashCode().mod(17) - 8) / 10f

    /** Daily swing: about -1 °C around 5 a.m., +1 °C around 5 p.m. */
    fun cycleJournalier(date: LocalDateTime): Float {
        val heure = date.hour + date.minute / 60.0
        return sin((heure - 11) / 24 * 2 * PI).toFloat()
    }

    /** Temperature the box settles at under [consigne] at [date]. */
    fun cible(consigne: Float, nomBoitier: String, date: LocalDateTime): Float =
        consigne + decalage(nomBoitier) + cycleJournalier(date)

    /** Next reading: moves part of the way from [actuelle] towards [cible], with sensor noise. */
    fun suivante(actuelle: Float, cible: Float, random: Random = Random): Float =
        actuelle + (cible - actuelle) * 0.3f + (random.nextFloat() - 0.5f) * 0.3f
}
