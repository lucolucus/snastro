package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.VoceRef

/**
 * Published-language view of one Voce of a Trascritto: its [intervalli] only, never [testo] (AC-48,
 * boundary `voci-per-parlanti`) — ordered by inizio (tie: `SegmentoId`), the order [Trascritto.voci]'s
 * `Voce.segmenti` already guarantees (INV-7).
 */
public data class VoceVista(val voceRef: VoceRef, val intervalli: List<IntervalloMs>)
