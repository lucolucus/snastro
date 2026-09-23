package snastro.progetto.adattatori.audio

import org.junit.jupiter.api.io.TempDir
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ArchivioAudioContratto
import java.nio.file.Files
import java.nio.file.Path

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real against
 * [ArchivioAudioFile] (AC-148) — plain java.nio, no native library, so this class runs in the
 * default gate (unlike [SondaAudioFfmpegTest]).
 */
class ArchivioAudioFileTest : ArchivioAudioContratto() {

    @TempDir
    lateinit var cartellaProgetto: Path

    @TempDir
    lateinit var cartellaSorgenti: Path

    override fun ambiente(): Ambiente = AmbienteFile(cartellaProgetto, cartellaSorgenti)

    private class AmbienteFile(
        private val cartellaProgetto: Path,
        private val cartellaSorgenti: Path,
    ) : Ambiente {
        override val archivio: ArchivioAudio = ArchivioAudioFile(cartellaProgetto)

        override fun sorgente(nomeFile: String, contenuto: ByteArray): String {
            val file = cartellaSorgenti.resolve(nomeFile)
            Files.write(file, contenuto)
            return file.toString()
        }

        // Mai creata: la copia fallisce perche' la sorgente non esiste (NoSuchFileException),
        // uno scenario reale quanto un guasto a meta' (es. il file scelto e' stato rimosso nel
        // frattempo) e portabile senza permessi/trucchi non standard.
        override fun sorgenteCheFallisce(nomeFile: String): String = cartellaSorgenti.resolve(nomeFile).toString()

        override fun creaNelProgetto(percorsoRelativo: String, contenuto: ByteArray) {
            val file = cartellaProgetto.resolve(percorsoRelativo)
            file.parent?.let(Files::createDirectories)
            Files.write(file, contenuto)
        }

        override fun contenutoNelProgetto(percorsoRelativo: String): ByteArray? {
            val file = cartellaProgetto.resolve(percorsoRelativo)
            return if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
        }

        override fun fileInAudio(): Set<String> {
            val cartellaAudio = cartellaProgetto.resolve("audio")
            if (!Files.isDirectory(cartellaAudio)) return emptySet()
            return Files.list(cartellaAudio).use { flusso ->
                flusso.filter(Files::isRegularFile).map { "audio/${it.fileName}" }.toList().toSet()
            }
        }
    }
}
