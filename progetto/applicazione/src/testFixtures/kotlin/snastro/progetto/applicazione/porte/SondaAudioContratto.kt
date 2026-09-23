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
    /** Three source files prepared by the implementation under test (paths are opaque strings). */
    public interface Ambiente {
        public val sonda: SondaAudio

        /** A readable audio file of a supported format. */
        public val fileLeggibile: String

        /** The date of [fileLeggibile] as the file system reports it. */
        public val dataDelFileLeggibile: LocalDate

        /** A file that cannot be read as audio (e.g. corrupted or missing). */
        public val fileIlleggibile: String

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
        val errore = a.sonda.sonda(a.fileIlleggibile).erroreAtteso<ErroreAudioProgetto.AudioNonLeggibile>()
        assertEquals(a.fileIlleggibile, errore.percorsoSorgente)
    }

    @Test
    public fun `AC-26 un formato non supportato da FormatoNonSupportato`() {
        val a = ambiente()
        val errore = a.sonda.sonda(a.fileFormatoNonSupportato)
            .erroreAtteso<ErroreAudioProgetto.FormatoNonSupportato>()
        assertEquals(a.fileFormatoNonSupportato, errore.percorsoSorgente)
    }
}
