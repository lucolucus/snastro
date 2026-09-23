package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio

/**
 * Who speaks when (boundary `tec-diarizzatore`, ADR 0004). Every [Turno] lies within the duration of the
 * samples; no speech (empty or silent samples) is an empty list, never an exception. Infra/native faults
 * throw (ADR 0003). Contract: `DiarizzatoreContratto`.
 */
public interface Diarizzatore {
    public fun diarizza(c: CampioniAudio): List<Turno>
}
