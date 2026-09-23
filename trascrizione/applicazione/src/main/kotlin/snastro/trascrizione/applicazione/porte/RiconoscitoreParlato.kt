package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio

/**
 * Speech to text (boundary `tec-riconoscitore`, ADR 0004). Tokens, when present, lie within the duration
 * of the given samples; empty samples give a blank text, never an exception. Infra/native faults throw
 * (ADR 0003). Contract: `RiconoscitoreParlatoContratto`.
 */
public interface RiconoscitoreParlato {
    public fun riconosci(c: CampioniAudio): Riconoscimento
}
