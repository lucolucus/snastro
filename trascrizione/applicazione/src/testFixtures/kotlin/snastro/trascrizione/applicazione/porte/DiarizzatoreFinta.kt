package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.trascrizione.dominio.NumeroPersone

/**
 * Deterministic [Diarizzatore]; samples without speech (empty or silent) give no Turno. With [turni] it returns
 * those that end within the duration of the samples (a scripted diarization); without, one Turno of voce 0 per
 * run of non-silent milliseconds. With a Numero di persone k, every `voceIndice` above k - 1 becomes k - 1 (at most
 * k distinct voices, ADR 0014). Records every [NumeroPersone] it receives, in order ([numeroPersoneRicevuti]).
 */
public class DiarizzatoreFinta(private val turni: List<Turno>? = null) : Diarizzatore {
    private val ricevuti = mutableListOf<NumeroPersone?>()

    /** The `numeroPersone` argument of every [diarizza] call, oldest first (`null` = absent). */
    public val numeroPersoneRicevuti: List<NumeroPersone?> get() = ricevuti.toList()

    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        ricevuti += numeroPersone
        val parlato = intervalliDiParlato(c)
        if (parlato.isEmpty()) return emptyList()
        val tutti = turni?.filter { it.intervallo.fineMs <= c.durataMs() } ?: parlato.map { Turno(it, voceIndice = 0) }
        val massimo = numeroPersone?.let { it.valore - 1 } ?: return tutti
        return tutti.map { it.copy(voceIndice = it.voceIndice.coerceAtMost(massimo)) }
    }
}
