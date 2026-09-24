package snastro.ml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.CampioniAudio
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gate tests (AC-388, AC-33, AC-254): no native library or real recognizer is ever loaded here —
 * [carica] is a counting fake, and [OfflineRecognizerConfig][com.k2fsa.sherpa.onnx.OfflineRecognizerConfig]
 * itself is pure Java (no native method), so building it is safe without natives.
 */
class RiconoscitoreSherpaTest {
    @TempDir
    lateinit var tmp: Path

    private val modello = ModelloTransducer(
        encoder = Path.of("encoder.onnx"),
        decoder = Path.of("decoder.onnx"),
        joiner = Path.of("joiner.onnx"),
        tokens = Path.of("tokens.txt"),
    )

    private fun cartellaConLibrerie(): Path {
        val cartella = Files.createDirectories(tmp.resolve("risorse"))
        LIBRERIE_NATIVE.forEach { Files.createFile(cartella.resolve(it)) }
        return cartella
    }

    private fun motore(): MotoreSherpa =
        MotoreSherpa(ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie().toString())) {}

    private class MotoreRiconoscimentoFinto(private val risultato: RisultatoRiconoscimento) : MotoreRiconoscimento {
        var decodifiche: Int = 0
            private set
        var rilasciato: Boolean = false
            private set

        override fun decodifica(campioni: CampioniAudio): RisultatoRiconoscimento {
            decodifiche++
            return risultato
        }

        override fun rilascia() {
            rilasciato = true
        }
    }

    @Test
    fun `AC-388 N chiamate a riconosci senza chiudi caricano il modello una sola volta`() {
        var caricamenti = 0
        val caricati = mutableListOf<MotoreRiconoscimentoFinto>()
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") { _ ->
            caricamenti++
            MotoreRiconoscimentoFinto(RisultatoRiconoscimento("ciao", null)).also(caricati::add)
        }
        val campioni = CampioniAudio(FloatArray(160) { 0.1f })

        val r1 = riconoscitore.riconosci(campioni)
        riconoscitore.riconosci(campioni)
        val r3 = riconoscitore.riconosci(campioni)

        assertEquals(1, caricamenti)
        assertEquals(3, caricati.single().decodifiche)
        assertEquals(RisultatoRiconoscimento("ciao", null), r1)
        assertEquals(r1, r3)
    }

    @Test
    fun `AC-388 AC-254 chiudi rilascia il modello caricato e la chiamata successiva lo ricarica`() {
        var caricamenti = 0
        val caricati = mutableListOf<MotoreRiconoscimentoFinto>()
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") { _ ->
            caricamenti++
            MotoreRiconoscimentoFinto(RisultatoRiconoscimento("", null)).also(caricati::add)
        }
        val campioni = CampioniAudio(FloatArray(160) { 0.1f })

        riconoscitore.riconosci(campioni)
        riconoscitore.chiudi()
        riconoscitore.riconosci(campioni)

        assertEquals(2, caricamenti)
        assertTrue(caricati[0].rilasciato)
        assertFalse(caricati[1].rilasciato)
    }

    @Test
    fun `AC-254 chiudi senza aver mai caricato non fa nulla`() {
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") {
            error("chiudi senza caricamenti precedenti non deve caricare il modello")
        }

        riconoscitore.chiudi() // no exception
    }

    @Test
    fun `AC-33 campioni vuoti non caricano nulla e restituiscono testo vuoto senza token`() {
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") {
            error("campioni vuoti non devono caricare il modello")
        }

        val r = riconoscitore.riconosci(CampioniAudio(FloatArray(0)))

        assertEquals(RisultatoRiconoscimento("", null), r)
    }

    private companion object {
        const val PROPRIETA_RISORSE_COMPOSE = "compose.application.resources.dir"
        val LIBRERIE_NATIVE = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)
    }
}
