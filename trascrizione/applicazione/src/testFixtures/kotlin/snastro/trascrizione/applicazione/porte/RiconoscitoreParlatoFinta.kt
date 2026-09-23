package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio

/**
 * Deterministic [RiconoscitoreParlato]: one token `parolaN` per run of non-silent milliseconds, the text is
 * the tokens joined by spaces (blank on silence). [conToken] = false models a model without timestamps.
 */
public class RiconoscitoreParlatoFinta(private val conToken: Boolean = true) : RiconoscitoreParlato {
    override fun riconosci(c: CampioniAudio): Riconoscimento {
        val token = intervalliDiParlato(c).mapIndexed { i, intervallo -> Token("parola${i + 1}", intervallo) }
        return Riconoscimento(token.joinToString(" ") { it.testo }, token.takeIf { conToken })
    }
}
