package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs

/** Synthetic [Vad]: every run of non-silent milliseconds is one speech interval (deterministic). */
public class VadFinta : Vad {
    override fun parlato(c: CampioniAudio): List<IntervalloMs> = intervalliDiParlato(c)
}
