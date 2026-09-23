package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs

/**
 * Voice activity detection (boundary `tec-vad`, ADR 0004): the speech intervals of the samples, ordered,
 * non-overlapping, within their duration; no speech is an empty list. Infra/native faults throw (ADR 0003).
 * Contract: `VadContratto`.
 */
public interface Vad {
    public fun parlato(c: CampioniAudio): List<IntervalloMs>
}
