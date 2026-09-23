package snastro.progetto.applicazione.porte

class ArchivioAudioFintaTest : ArchivioAudioContratto() {
    override fun ambiente(): Ambiente {
        val archivio = ArchivioAudioFinta { it.startsWith(GUASTE) }
        return object : Ambiente {
            override val archivio = archivio

            override fun sorgente(nomeFile: String) = "/sorgenti/$nomeFile"

            override fun sorgenteCheFallisce(nomeFile: String) = "$GUASTE$nomeFile"

            override fun fileInAudio() = archivio.archiviati.map { it.percorsoRelativo }.toSet()
        }
    }

    private companion object {
        const val GUASTE = "/guaste/"
    }
}
