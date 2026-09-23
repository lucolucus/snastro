package snastro.progetto.applicazione.porte

class ArchivioAudioFintaTest : ArchivioAudioContratto() {
    override fun ambiente(): Ambiente {
        val archivio = ArchivioAudioFinta()
        return object : Ambiente {
            override val archivio = archivio

            override fun sorgente(nomeFile: String, contenuto: ByteArray) =
                "/sorgenti/$nomeFile".also { archivio.conSorgente(it, contenuto) }

            override fun sorgenteCheFallisce(nomeFile: String) =
                "/guaste/$nomeFile".also { archivio.conSorgenteCheFallisce(it) }

            override fun creaNelProgetto(percorsoRelativo: String, contenuto: ByteArray) {
                archivio.conFileNelProgetto(percorsoRelativo, contenuto)
            }

            override fun contenutoNelProgetto(percorsoRelativo: String) = archivio.contenuto(percorsoRelativo)

            override fun fileInAudio() = archivio.archiviati.map { it.percorsoRelativo }.toSet()
        }
    }
}
