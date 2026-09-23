package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.assertEquals

/**
 * Contract of [ArchivioAudio] (boundary `tec-sonda-archivio`): the copy of a source into the
 * project's `audio/` folder mints `audio/<registrazioneId>.<source extension lowercased>` (ADR 0010);
 * a failed copy leaves no file behind. One subclass per implementation (`ArchivioAudioFinta` here,
 * the file-copy adapter in `:progetto:adattatori`).
 */
public abstract class ArchivioAudioContratto {
    /** A fresh project folder with an empty `audio/`, plus the source files the tests copy from. */
    public interface Ambiente {
        public val archivio: ArchivioAudio

        /** Prepares a readable source file named [nomeFile] and returns its path. */
        public fun sorgente(nomeFile: String): String

        /** Prepares a source named [nomeFile] whose copy fails partway and returns its path. */
        public fun sorgenteCheFallisce(nomeFile: String): String

        /** Paths, relative to the project folder, of every file now in `audio/` (partial ones included). */
        public fun fileInAudio(): Set<String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-27 copia restituisce audio-registrazioneId-estensione minuscola e il file e in audio`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta del 12 marzo.M4A"), ID).atteso()
        assertEquals(RiferimentoAudio("audio/id-1.m4a"), riferimento)
        assertEquals(setOf("audio/id-1.m4a"), a.fileInAudio())
    }

    @Test
    public fun `AC-27 una copia fallita da CopiaFallita e non lascia file in audio`() {
        val a = ambiente()
        val sorgente = a.sorgenteCheFallisce("Seduta del 12 marzo.m4a")
        val errore = a.archivio.copia(sorgente, ID).erroreAtteso<ErroreAudioProgetto.CopiaFallita>()
        assertEquals(sorgente, errore.percorsoSorgente)
        assertEquals(emptySet(), a.fileInAudio())
    }

    @Test
    public fun `AC-27 scarta rimuove il file copiato`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta del 12 marzo.m4a"), ID).atteso()
        a.archivio.scarta(riferimento)
        assertEquals(emptySet(), a.fileInAudio())
    }

    private companion object {
        val ID = RegistrazioneId("id-1")
    }
}
