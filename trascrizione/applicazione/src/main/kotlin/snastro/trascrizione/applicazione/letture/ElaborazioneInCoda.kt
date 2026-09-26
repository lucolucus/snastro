package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import java.time.Instant

/**
 * One `in_attesa` Elaborazione as the shared queue (`:avvio`, ADR 0023 §1) sees it: [elaborazioneId]
 * is a plain `String` (not [snastro.kernel.ElaborazioneId]) ON PURPOSE — the queue is context-agnostic
 * and stays over primitive ids only, so it never depends on Trascrizione's Published Language types.
 * [creataAlle] is the FIFO key (AC-S23), minted by `AvviaElaborazione` from the injected clock.
 */
public data class ElaborazioneInCoda(
    public val elaborazioneId: String,
    public val registrazioneId: RegistrazioneId,
    public val creataAlle: Instant,
)
