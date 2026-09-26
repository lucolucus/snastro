package snastro.trascrizione.applicazione.letture

import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import java.time.Instant

/**
 * One row of [StatiElaborazione] (AC-162, the `stati-elaborazione` `view_shape`): the processing state of
 * one Registrazione, as S2 (`RegistrazioniDelProgetto`) joins it. [stato], [fase], [avviataAlle],
 * [motivoFallimento], [numeroPersone] and [elaborazioneId] come from the LATEST Elaborazione
 * (by `creataAlle`, then id): [fase] is only ever non-null for [StatoElaborazioneVista.IN_CORSO] (AC-164);
 * [motivoFallimento] only for `FALLITA`; [numeroPersone] is
 * `null` when absent or `NON_AVVIATA` (S2 prefills 'Riprova'/'Ritrascrivi' with it, AC-376); [elaborazioneId] is
 * `null` only for `NON_AVVIATA` (the id `AnnullaElaborazione` takes, AC-474). [trascrittoDisponibile] and
 * [numVoci] come from the Trascritto, whatever the latest run's state (ADR 0018, AC-165/AC-447): S2/S3 derive
 * the 'Ritrascrizione …' states from the pair [stato] + [trascrittoDisponibile].
 *
 * ADR 0023 §4 (block `avvio-coda-condivisa`): the queue position for `IN_ATTESA` no longer lives here —
 * `posizioneInCoda` is REMOVED (one source of truth, `:ui`'s `PosizioniNellaCoda`, computed by the shared
 * queue's owner `:avvio`). S2's presenter joins it by [registrazioneId] on its own.
 */
public data class StatoRegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val stato: StatoElaborazioneVista,
    val fase: FaseElaborazione?,
    val avviataAlle: Instant?,
    val motivoFallimento: String?,
    val numVoci: Int?,
    val numeroPersone: Int?,
    val trascrittoDisponibile: Boolean,
    val elaborazioneId: ElaborazioneId?,
)
