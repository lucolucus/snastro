package snastro.parlanti.adattatori.ml

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.audio.DecodificaFfmpeg
import snastro.audio.SondaFfmpeg
import snastro.kernel.CampioniAudio
import snastro.ml.MotoreSherpa
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaContratto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [@modelli, opt-in] the REAL [EstrattoreImprontaSherpa]: real sherpa-onnx natives and the real TitaNet-small
 * file (`nemo_en_titanet_small.onnx`, ADR 0019 §1.7) from `SNASTRO_MODELLI_R1_DIR` (a local download, never
 * committed). AC-258: the port's contract passes against it. AC-493: 1 000 extractions of 1–10 s over a
 * real recording read ONLY from the path in `SNASTRO_AUDIO_VIA_ROQUEL` (skipped when unset); only the time
 * is printed.
 */
@Tag("modelli")
class EstrattoreImprontaSherpaModelliTest : EstrattoreImprontaContratto() {
    private val reale by lazy { EstrattoreImprontaSherpa(MotoreSherpa(), modello(), threadIntraOp = THREAD) }

    override fun estrattore(): EstrattoreImpronta = reale

    @Test
    fun `AC-493 1000 estrai su 1000 intervalli distinti di 1-10 s richiedono al massimo 60 s`() {
        val percorso = System.getenv(AUDIO)
        assumeTrue(percorso != null && Files.isRegularFile(Path.of(percorso)), "$AUDIO non impostata")
        val wav = Files.createTempFile("ac-493-", ".wav") // the canonical 16 kHz WAV :audio reads, deleted below
        val tutti = try {
            DecodificaFfmpeg().decodificaInWav(Path.of(percorso), wav)
            DecodificaFfmpeg().leggiCampioni(wav, 0, SondaFfmpeg().sonda(wav).durataMs)
        } finally {
            Files.deleteIfExists(wav)
        }
        val intervalli = List(N_ESTRAZIONI) { i -> i * PASSO_CAMPIONI to (1 + i % 10) * HZ }
        assertTrue(intervalli.last().let { (inizio, n) -> inizio + n <= tutti.size }, "registrazione troppo corta")
        reale.estrai(CampioniAudio(tutti.copyOfRange(0, HZ))) // model loaded before the clock starts

        val t0 = System.nanoTime()
        intervalli.forEach { (inizio, n) -> reale.estrai(CampioniAudio(tutti.copyOfRange(inizio, inizio + n))) }
        val secondi = (System.nanoTime() - t0) / NANO_PER_S

        println("MISURA AC-493 $N_ESTRAZIONI estrai in ${"%.1f".format(secondi)} s (limite $LIMITE_S s)")
        assertTrue(secondi <= LIMITE_S, "$secondi s")
        reale.chiudi()
    }

    private fun modello(): Path {
        val cartella = assertNotNull(
            System.getenv(MODELLI),
            "$MODELLI non impostata: cartella dei modelli @modelli",
        )
        return Path.of(cartella).resolve("nemo_en_titanet_small.onnx")
    }

    private companion object {
        const val MODELLI = "SNASTRO_MODELLI_R1_DIR"
        const val AUDIO = "SNASTRO_AUDIO_VIA_ROQUEL"
        const val THREAD = 6
        const val HZ = 16_000
        const val N_ESTRAZIONI = 1_000
        const val PASSO_CAMPIONI = 4 * HZ // interval i starts at 4·i s: 1 000 distinct intervals over ~67 min
        const val LIMITE_S = 60.0
        const val NANO_PER_S = 1e9
    }
}
