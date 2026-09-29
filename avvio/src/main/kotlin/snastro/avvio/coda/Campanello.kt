package snastro.avvio.coda

import kotlinx.coroutines.channels.Channel

/**
 * The shared queue's wake-up handle (ADR 0030 §1 step 4, AC-C70): created by `apriProgetto` BEFORE the modules and
 * handed to the ones that enqueue (Trascrizione's `AvviaElaborazione`, Sintesi's `RiassuntoRichiesto`) and to the
 * [CodaCondivisa] that consumes it — so no module ever needs a reference to the queue, and no late-bound one exists.
 * Rings coalesce (conflated); ringing before the queue starts, or after it stopped, is harmless.
 */
internal class Campanello {
    internal val segnali = Channel<Unit>(Channel.CONFLATED)

    /** Asks the queue for one more tick; never blocks, callable from any thread. */
    fun suona() {
        segnali.trySend(Unit)
    }
}
