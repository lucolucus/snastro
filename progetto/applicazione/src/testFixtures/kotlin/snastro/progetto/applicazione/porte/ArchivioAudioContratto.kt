package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Contract of [ArchivioAudio] (boundary `tec-sonda-archivio`): the copy of a source into the
 * project's `audio/` folder mints `audio/<registrazioneId>.<source extension lowercased>` (ADR 0010),
 * byte for byte; a failed copy leaves no file behind; [ArchivioAudio.scarta] is idempotent and
 * confined to `audio/`. One subclass per implementation (`ArchivioAudioFinta` here, the file-copy
 * adapter in `:progetto:adattatori`).
 */
public abstract class ArchivioAudioContratto {
    /** A fresh project folder with an empty `audio/`, plus the source files the tests copy from. */
    public interface Ambiente {
        public val archivio: ArchivioAudio

        /** Prepares a readable source file named [nomeFile] holding [contenuto] and returns its path. */
        public fun sorgente(nomeFile: String, contenuto: ByteArray): String

        /** Prepares a source named [nomeFile] whose copy fails partway and returns its path. */
        public fun sorgenteCheFallisce(nomeFile: String): String

        /** Writes [contenuto] at [percorsoRelativo] in the project folder (outside `audio/` too). */
        public fun creaNelProgetto(percorsoRelativo: String, contenuto: ByteArray)

        /** The bytes of the project file at [percorsoRelativo], or null if there is no such file. */
        public fun contenutoNelProgetto(percorsoRelativo: String): ByteArray?

        /** Paths, relative to the project folder, of every file now in `audio/` (partial ones included). */
        public fun fileInAudio(): Set<String>
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-27 copia restituisce audio-registrazioneId-estensione minuscola con lo stesso contenuto`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta del 12 marzo.M4A", CONTENUTO), ID).atteso()
        assertEquals(RiferimentoAudio("audio/id-1.m4a"), riferimento)
        assertEquals(setOf("audio/id-1.m4a"), a.fileInAudio())
        assertContentEquals(CONTENUTO, a.contenutoNelProgetto("audio/id-1.m4a"))
    }

    @Test
    public fun `AC-27 una sorgente senza estensione diventa audio-registrazioneId`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta", CONTENUTO), ID).atteso()
        assertEquals(RiferimentoAudio("audio/id-1"), riferimento)
        assertContentEquals(CONTENUTO, a.contenutoNelProgetto("audio/id-1"))
    }

    @Test
    public fun `AC-27 una copia fallita da CopiaFallita e non lascia file in audio`() {
        val a = ambiente()
        val sorgente = a.sorgenteCheFallisce("Seduta del 12 marzo.m4a")
        val errore = a.archivio.copia(sorgente, ID).erroreAtteso<ErroreApplicazioneProgetto.CopiaFallita>()
        assertEquals(sorgente, errore.percorsoSorgente)
        assertEquals(emptySet(), a.fileInAudio())
    }

    @Test
    public fun `AC-27 scarta rimuove il file copiato`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta del 12 marzo.m4a", CONTENUTO), ID).atteso()
        a.archivio.scarta(riferimento)
        assertEquals(emptySet(), a.fileInAudio())
    }

    @Test
    public fun `AC-27 scarta e idempotente e un file mancante non e un errore`() {
        val a = ambiente()
        val riferimento = a.archivio.copia(a.sorgente("Seduta del 12 marzo.m4a", CONTENUTO), ID).atteso()
        a.archivio.scarta(riferimento)
        a.archivio.scarta(riferimento)
        a.archivio.scarta(RiferimentoAudio("audio/mai-copiato.m4a"))
        assertEquals(emptySet(), a.fileInAudio())
    }

    @Test
    public fun `AC-27 scarta non tocca nulla fuori da audio`() {
        val a = ambiente()
        a.creaNelProgetto("documenti/nota.md", CONTENUTO)
        a.creaNelProgetto("progetto.db", CONTENUTO)
        a.archivio.scarta(RiferimentoAudio("documenti/nota.md"))
        a.archivio.scarta(RiferimentoAudio("audio/../progetto.db"))
        assertContentEquals(CONTENUTO, a.contenutoNelProgetto("documenti/nota.md"))
        assertContentEquals(CONTENUTO, a.contenutoNelProgetto("progetto.db"))
    }

    private companion object {
        val ID = RegistrazioneId("id-1")

        /** Large enough to span several I/O buffers, patterned so a truncated or shifted copy differs. */
        val CONTENUTO = ByteArray(70_000) { (it % 251).toByte() }
    }
}
