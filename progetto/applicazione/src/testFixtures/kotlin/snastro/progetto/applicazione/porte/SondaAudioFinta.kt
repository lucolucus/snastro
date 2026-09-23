package snastro.progetto.applicazione.porte

import snastro.kernel.Esito

/**
 * [SondaAudio] over fixed answers: a path in [nonSupportati] → FormatoNonSupportato, a path in
 * [leggibili] → its [InfoAudio], any other path → AudioNonLeggibile.
 */
public class SondaAudioFinta(
    private val leggibili: Map<String, InfoAudio> = emptyMap(),
    private val nonSupportati: Set<String> = emptySet(),
) : SondaAudio {
    override fun sonda(percorsoSorgente: String): Esito<InfoAudio> {
        val info = leggibili[percorsoSorgente]
        return when {
            percorsoSorgente in nonSupportati ->
                Esito.Errore(ErroreAudioProgetto.FormatoNonSupportato(percorsoSorgente))
            info != null -> Esito.Ok(info)
            else -> Esito.Errore(ErroreAudioProgetto.AudioNonLeggibile(percorsoSorgente))
        }
    }
}
