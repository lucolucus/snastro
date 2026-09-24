package snastro.trascrizione.adattatori.ml

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.ml.ConfigSessione
import snastro.ml.ModelloTransducer
import snastro.ml.MotoreSherpa
import snastro.ml.RiconoscitoreSherpa
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoContratto
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * AC-252: [RiconoscitoreParlatoContratto] against the real Parakeet adapter, on real speech
 * (`test_wavs/en.wav`, shipped inside the model archive — NEVER the project's own `sample/`
 * recordings: a synthetic tone, the contract's default `parlato()`, would not do for a real speech
 * model). The model directory comes from `SNASTRO_MODELLO_RICONOSCITORE`: `:trascrizione:adattatori`
 * has no dependency edge on `:modelli` (architecture.md), so resolving the real per-OS cache path by
 * catalogue id is a composition-root concern, not this test's.
 */
@Tag("modelli")
class RiconoscitoreParlatoSherpaContrattoTest : RiconoscitoreParlatoContratto() {
    override fun riconoscitore(): RiconoscitoreParlato = RiconoscitoreParlatoSherpa(motoreDiProva())

    override fun parlato(): CampioniAudio = CampioniAudio(wav16kMono(cartellaTestWavs().resolve("en.wav")))

    /** Not one of the inherited ACs: surfaces the recognized text for a human to read (worker report). */
    @Test
    fun `AC-252 il testo riconosciuto di test_wavs en wav non e vuoto`() {
        val r = riconoscitore().riconosci(parlato())

        assertTrue(r.testo.isNotBlank(), "testo riconosciuto: '${r.testo}'")
        println("AC-252 riconoscitore-sherpa — testo riconosciuto (test_wavs/en.wav): ${r.testo}")
    }
}

private fun motoreDiProva(): RiconoscitoreSherpa {
    val dir = percorsoModelloParakeet()
    val modello = ModelloTransducer(
        encoder = dir.resolve("encoder.int8.onnx"),
        decoder = dir.resolve("decoder.int8.onnx"),
        joiner = dir.resolve("joiner.int8.onnx"),
        tokens = dir.resolve("tokens.txt"),
    )
    return RiconoscitoreSherpa(MotoreSherpa(), modello, ConfigSessione.coreDiPrestazione())
}

private fun cartellaTestWavs(): Path = percorsoModelloParakeet().resolve("test_wavs")

private fun percorsoModelloParakeet(): Path {
    val percorso = System.getenv("SNASTRO_MODELLO_RICONOSCITORE")
        ?: error(
            "imposta SNASTRO_MODELLO_RICONOSCITORE alla directory del modello Parakeet estratto " +
                "(encoder.int8.onnx, decoder.int8.onnx, joiner.int8.onnx, tokens.txt, test_wavs/)",
        )
    return Path.of(percorso)
}
