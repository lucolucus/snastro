package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio

/**
 * Deterministic [Diarizzatore]; samples without speech (empty or silent) give no Turno. With [turni] it returns
 * those that end within the duration of the samples (a scripted diarization); without, one Turno of voce 0 per
 * run of non-silent milliseconds.
 */
public class DiarizzatoreFinta(private val turni: List<Turno>? = null) : Diarizzatore {
    override fun diarizza(c: CampioniAudio): List<Turno> {
        val parlato = intervalliDiParlato(c)
        if (parlato.isEmpty()) return emptyList()
        return turni?.filter { it.intervallo.fineMs <= c.durataMs() } ?: parlato.map { Turno(it, voceIndice = 0) }
    }
}
