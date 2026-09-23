package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio

/**
 * Trascrizione's decoding port (boundary `tec-decodifica-trascrizione`, ADR 0005): the source is decoded
 * once to 16 kHz mono and then read back. Infra faults (unreadable source, missing decoded audio) THROW an
 * exception (ADR 0003) — never an `Esito`:
 * - [decodifica] of an unreadable source (missing or not audio) throws `java.io.IOException`;
 * - [campioni] returns exactly `(fineMs - inizioMs) * 16` samples — that slice of [tutti], zero-padded
 *   past the end of the decoded audio (an interval starting at or after the end is all zeros).
 *
 * Contract: `DecodificatoreAudioContratto`.
 */
public interface DecodificatoreAudio {
    /** Decodes [sorgente] (the `decodifica` FaseElaborazione) so that [tutti] and [campioni] can read [id]. */
    public fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio)

    /** Every sample of the decoded Registrazione [id]. */
    public fun tutti(id: RegistrazioneId): CampioniAudio

    /**
     * The samples of [intervallo]: exactly `(fineMs - inizioMs) * 16` of them, the same as that slice of
     * [tutti], zero-padded past the end of the decoded audio.
     */
    public fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio
}
