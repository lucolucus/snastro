package snastro.supporto.test

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * A [Clock] that moves only when told (ADR 0028 §3): [avanza] / [imposta] separate instants, never a
 * `Thread.sleep`. Thread-safe.
 */
public class OrologioFinto(inizio: Instant, private val zona: ZoneId = ZoneOffset.UTC) : Clock() {
    @Volatile private var adesso: Instant = inizio

    /** Moves the clock forward by [durata]. */
    public fun avanza(durata: Duration) {
        synchronized(this) { adesso += durata.toJavaDuration() }
    }

    /** Sets the clock to [istante]. */
    public fun imposta(istante: Instant) {
        adesso = istante
    }

    override fun instant(): Instant = adesso

    override fun getZone(): ZoneId = zona

    override fun withZone(zone: ZoneId): Clock = OrologioFintoInZona(this, zone)

    /** A view in another zone that follows the same instant. */
    private class OrologioFintoInZona(private val base: OrologioFinto, private val zona: ZoneId) : Clock() {
        override fun instant(): Instant = base.instant()

        override fun getZone(): ZoneId = zona

        override fun withZone(zone: ZoneId): Clock = OrologioFintoInZona(base, zone)
    }
}
