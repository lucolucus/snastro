package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract of [SondaAudio] (boundary `tec-sonda-archivio`). One subclass per implementation
 * (`SondaAudioFinta` here, the `:audio`-backed adapter in `:progetto:adattatori`, tagged `modelli`).
 */
public abstract class SondaAudioContratto {
    /** Source files prepared by the implementation under test (paths are opaque strings). */
    public interface Ambiente {
        public val sonda: SondaAudio

        /** A readable audio file of a supported format. */
        public val fileLeggibile: String

        /** The date of [fileLeggibile] as the file system reports it. */
        public val dataDelFileLeggibile: LocalDate

        /** An existing file that cannot be decoded as audio (e.g. corrupted). */
        public val fileIlleggibile: String

        /** An existing file of zero length, with a supported extension. */
        public val fileVuoto: String

        /** A directory, not a file. */
        public val cartella: String

        /** A path where nothing exists. */
        public val fileInesistente: String

        /** A readable file whose format is not supported. */
        public val fileFormatoNonSupportato: String
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-26 un file leggibile da una durata positiva e la data del file`() {
        val a = ambiente()
        val info = a.sonda.sonda(a.fileLeggibile).atteso()
        assertTrue(info.durataMs > 0, "durata ${info.durataMs} ms")
        assertEquals(a.dataDelFileLeggibile, info.dataFile)
    }

    @Test
    public fun `AC-26 un file illeggibile da AudioNonLeggibile`() {
        val a = ambiente()
        assertNonLeggibile(a, a.fileIlleggibile)
    }

    @Test
    public fun `AC-26 un file vuoto da AudioNonLeggibile`() {
        val a = ambiente()
        assertNonLeggibile(a, a.fileVuoto)
    }

    @Test
    public fun `AC-26 una cartella da AudioNonLeggibile`() {
        val a = ambiente()
        assertNonLeggibile(a, a.cartella)
    }

    @Test
    public fun `AC-26 un file inesistente da AudioNonLeggibile`() {
        val a = ambiente()
        assertNonLeggibile(a, a.fileInesistente)
    }

    @Test
    public fun `AC-26 un formato non supportato da FormatoNonSupportato`() {
        val a = ambiente()
        val errore = a.sonda.sonda(a.fileFormatoNonSupportato)
            .erroreAtteso<ErroreApplicazioneProgetto.FormatoNonSupportato>()
        assertEquals(a.fileFormatoNonSupportato, errore.percorsoSorgente)
    }

    private fun assertNonLeggibile(a: Ambiente, percorso: String) {
        val errore = a.sonda.sonda(percorso).erroreAtteso<ErroreApplicazioneProgetto.AudioNonLeggibile>()
        assertEquals(percorso, errore.percorsoSorgente)
    }
}
