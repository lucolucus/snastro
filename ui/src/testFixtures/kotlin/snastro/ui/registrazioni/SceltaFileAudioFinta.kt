package snastro.ui.registrazioni

/** Fake [SceltaFileAudio] (RC-9): always answers [risultato] (empty = the user cancelled), counting the asks. */
class SceltaFileAudioFinta(private val risultato: List<String> = emptyList()) : SceltaFileAudio {
    var richieste: Int = 0
        private set

    override fun scegli(): List<String> {
        richieste++
        return risultato
    }
}
