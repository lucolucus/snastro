package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId

/**
 * Parlanti's own decoding port (boundary `tec-decodifica-parlanti`, ADR 0005): 16 kHz mono samples of
 * [intervalli], concatenated in the GIVEN order. Infra faults throw (ADR 0003).
 * Contract: `DecodificatoreAudioContratto`.
 */
public interface DecodificatoreAudio {
    public fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio
}
