package snastro.ui.registrazione

import kotlinx.coroutines.flow.StateFlow
import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned UI port of "Riassegna per somiglianza" (boundary `ui-azioni-somiglianza`, ADR 0019 §6 +
 * Amendment (b).2): compute → PREVIEW → apply, in the scope of the OPEN PROJECT (leaving S3 cancels
 * nothing). Implemented by `avvio-parlanti` (the cross-context glue: `PianoRiassegnazioneQuery` then
 * `RiassegnaSegmenti` with the HELD plan); faked by `AzioniSomiglianzaFinta`.
 *
 * - [calcola] ends in [StatoSomiglianza.Anteprima] and writes nothing; ignored while a computation, a
 *   preview or an application of that Registrazione is already there.
 * - [applica] sends the HELD plan (never recomputed); ignored unless [StatoSomiglianza.Anteprima] with N > 0.
 * - [annulla] interrupts a computation or discards a preview, writing nothing; on a final
 *   [StatoSomiglianza.Esito] / [StatoSomiglianza.Errore] it clears the entry (the message was dismissed);
 *   never interrupts an application.
 * - [stato]: no entry = idle.
 *
 * All three are non-blocking by contract, yet the S3 presenter calls them on its background dispatcher
 * (AC-536).
 */
interface AzioniSomiglianza {
    val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>>

    fun calcola(id: RegistrazioneId)

    fun applica(id: RegistrazioneId)

    fun annulla(id: RegistrazioneId)
}
