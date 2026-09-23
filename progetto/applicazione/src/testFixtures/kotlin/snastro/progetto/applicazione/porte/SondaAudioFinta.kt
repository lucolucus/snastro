package snastro.progetto.applicazione.porte

import snastro.kernel.Esito

/**
 * [SondaAudio] over fixed answers: a path in [nonSupportati] → FormatoNonSupportato, a path in
 * [leggibili] → its [InfoAudio], any other path → AudioNonLeggibile.
 * Like the real probe it never answers Ok with a non-positive duration.
 */
public class SondaAudioFinta(
    private val leggibili: Map<String, InfoAudio> = emptyMap(),
    private val nonSupportati: Set<String> = emptySet(),
) : SondaAudio {
    init {
        require(leggibili.values.all { it.durataMs > 0 }) { "una sonda Ok ha sempre durataMs > 0" }
    }

    override fun sonda(percorsoSorgente: String): Esito<InfoAudio> {
        val info = leggibili[percorsoSorgente]
        return when {
            percorsoSorgente in nonSupportati ->
                Esito.Errore(ErroreApplicazioneProgetto.FormatoNonSupportato(percorsoSorgente))
            info != null -> Esito.Ok(info)
            else -> Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(percorsoSorgente))
        }
    }
}
