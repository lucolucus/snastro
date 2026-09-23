package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.trascrizione.dominio.NumeroPersone

/**
 * Who speaks when (boundary `tec-diarizzatore`, ADR 0004). Every [Turno] lies within the duration of the
 * samples; no speech (empty or silent samples) is an empty list, never an exception. Infra/native faults
 * throw (ADR 0003). With [numeroPersone] = k the Turni have at most k distinct `voceIndice` (may be fewer, never a
 * failure); `null` means automatic clustering (ADR 0014). Contract: `DiarizzatoreContratto`.
 */
public interface Diarizzatore {
    public fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno>
}
