package snastro.progetto.adattatori.audio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.RegistrazioneId
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ArchivioAudioContratto
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real against
 * [ArchivioAudioFile] (AC-148) — plain java.nio, no native library, so this class runs in the
 * default gate (unlike [SondaAudioFfmpegTest]). [AmbienteFile]'s `sorgenteCheFallisce` uses the
 * [PassoCopia] seam to make the inherited contract's "copia fallita" scenario genuinely fail
 * *partway* (bytes already in the temp file, then an [IOException]) instead of a source that
 * never existed — otherwise there is no temp file for the size check / cleanup to ever matter.
 * The two `AC-148 ...` tests below go further than the contract's hook allows: they build the
 * adapter directly against each of the two ways a real copy can leave a bad temp file behind —
 * (a) an I/O fault mid-write, (b) a copy that silently truncates without throwing at all — so
 * both the temp-cleanup-on-catch and the belt-and-braces size check are each proven on their own.
 */
class ArchivioAudioFileTest : ArchivioAudioContratto() {

    @TempDir
    lateinit var cartellaProgetto: Path

    @TempDir
    lateinit var cartellaSorgenti: Path

    override fun ambiente(): Ambiente = AmbienteFile(cartellaProgetto, cartellaSorgenti)

    @Test
    fun `AC-148 una IOException a meta copia non lascia file in audio`() {
        val sorgente = cartellaSorgenti.resolve("seduta.m4a")
        Files.write(sorgente, CONTENUTO)
        val archivio = ArchivioAudioFile(cartellaProgetto) { origine, temporaneo, _ ->
            Files.write(temporaneo, Files.readAllBytes(origine).copyOf(CONTENUTO.size / 2))
            throw IOException("copia interrotta (test)")
        }

        val errore = archivio.copia(sorgente.toString(), ID).erroreAtteso<ErroreApplicazioneProgetto.CopiaFallita>()

        assertEquals(sorgente.toString(), errore.percorsoSorgente)
        assertEquals(emptySet(), fileInAudio())
    }

    @Test
    fun `AC-148 una copia troncata senza eccezione e rifiutata dal controllo di dimensione`() {
        val sorgente = cartellaSorgenti.resolve("seduta.m4a")
        Files.write(sorgente, CONTENUTO)
        val archivio = ArchivioAudioFile(cartellaProgetto) { _, temporaneo, _ ->
            Files.write(temporaneo, CONTENUTO.copyOf(CONTENUTO.size / 2))
        }

        val errore = archivio.copia(sorgente.toString(), ID).erroreAtteso<ErroreApplicazioneProgetto.CopiaFallita>()

        assertEquals(sorgente.toString(), errore.percorsoSorgente)
        assertEquals(emptySet(), fileInAudio())
    }

    private fun fileInAudio(): Set<String> {
        val cartellaAudio = cartellaProgetto.resolve("audio")
        if (!Files.isDirectory(cartellaAudio)) return emptySet()
        return Files.list(cartellaAudio).use { flusso ->
            flusso.filter(Files::isRegularFile).map { "audio/${it.fileName}" }.toList().toSet()
        }
    }

    private class AmbienteFile(
        private val cartellaProgetto: Path,
        private val cartellaSorgenti: Path,
    ) : Ambiente {
        private val sorgentiCheFallisconoAMeta = mutableSetOf<Path>()

        override val archivio: ArchivioAudio = ArchivioAudioFile(cartellaProgetto) { sorgente, temporaneo, opzione ->
            if (sorgente in sorgentiCheFallisconoAMeta) {
                val bytes = Files.readAllBytes(sorgente)
                Files.write(temporaneo, bytes.copyOf(bytes.size / 2))
                throw IOException("copia interrotta (test)")
            } else {
                Files.copy(sorgente, temporaneo, opzione)
            }
        }

        override fun sorgente(nomeFile: String, contenuto: ByteArray): String {
            val file = cartellaSorgenti.resolve(nomeFile)
            Files.write(file, contenuto)
            return file.toString()
        }

        // Scrive davvero un sorgente e lo marca perche' passoCopia lo interrompa a meta': un
        // guasto reale quanto una sorgente sparita (es. il disco si riempie a meta' copia), ma
        // questo lascia un temporaneo parziale davvero da ripulire (AC-148).
        override fun sorgenteCheFallisce(nomeFile: String): String {
            val file = cartellaSorgenti.resolve(nomeFile)
            Files.write(file, CONTENUTO)
            sorgentiCheFallisconoAMeta.add(file)
            return file.toString()
        }

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

    private companion object {
        val ID = RegistrazioneId("id-1")

        /** Large enough to span several I/O buffers, patterned so a truncated copy differs. */
        val CONTENUTO = ByteArray(70_000) { (it % 251).toByte() }
    }
}
