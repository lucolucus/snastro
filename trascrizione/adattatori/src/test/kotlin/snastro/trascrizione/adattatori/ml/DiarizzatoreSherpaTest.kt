package snastro.trascrizione.adattatori.ml

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.audio.DecodificaFfmpeg
import snastro.audio.SondaFfmpeg
import snastro.kernel.CampioniAudio
import snastro.kernel.atteso
import snastro.ml.MotoreSherpa
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreContratto
import snastro.trascrizione.dominio.NumeroPersone
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real against
 * [DiarizzatoreSherpa] (AC-249) — real sherpa-onnx natives, real pyannote segmentation-3.0 +
 * WeSpeaker embedding (ADR 0014). Models come from [CARTELLA_MODELLI_ENV] (never `:modelli`'s
 * provisioning cache, never committed — the env var points at the spike's local scratch download,
 * per the profile instructions for `@Tag("modelli")` tests). [parlato] is never a `sample/`
 * recording (forbidden by the profile): two short clips sherpa itself ships as ASR `test_wavs`
 * (different languages, `qwen3-asr`'s own demo set — the best proxy for "different speakers" this
 * archive offers), decoded through `:audio`'s real FFmpeg (ADR 0005, never `javax.sound` here,
 * CR-3) and concatenated.
 */
@Tag("modelli")
class DiarizzatoreSherpaTest : DiarizzatoreContratto() {
    @TempDir
    lateinit var tmp: Path

    override fun diarizzatore(): Diarizzatore = DiarizzatoreSherpa(
        motore = MotoreSherpa(),
        percorsoSegmentazione = cartellaModelli().resolve("sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx"),
        percorsoEmbedding = cartellaModelli().resolve("wespeaker_en_voxceleb_resnet34_LM.onnx"),
        threadIntraOp = NUMERO_THREAD,
    )

    override fun parlato(): CampioniAudio = dueVociReali()

    /**
     * AC-373: `numeroPersone` = 10 is above the 1-2 real voices these clips hold — measured
     * (`DiarizzatoreSherpa` KDoc) to never throw. This pins that on the real adapter: the call
     * below must return normally (a thrown exception fails this test) and still respect the port's
     * "at most k distinct voceIndice" (already covered generically by the inherited AC-374 above,
     * with k = 10 in its own list — this test names the specific acceptance criterion).
     */
    @Test
    fun `AC-373 un numeroPersone oltre ai parlanti reali non fa fallire la diarizzazione`() {
        val turni = diarizzatore().diarizza(dueVociReali(), NumeroPersone.di(10).atteso())

        assertTrue(turni.map { it.voceIndice }.toSet().size <= 10)
    }

    private fun dueVociReali(): CampioniAudio {
        val cartellaClip = cartellaModelli().resolve("sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/test_wavs")
        val decodifica = DecodificaFfmpeg()
        val sonda = SondaFfmpeg()
        val campioni = listOf("de.wav", "fr1.wav").map { nome ->
            val wav = tmp.resolve(nome)
            decodifica.decodificaInWav(cartellaClip.resolve(nome), wav)
            decodifica.leggiCampioni(wav, 0, sonda.sonda(wav).durataMs)
        }
        return CampioniAudio(campioni[0] + campioni[1])
    }

    private fun cartellaModelli(): Path {
        val percorso = assertNotNull(
            System.getenv(CARTELLA_MODELLI_ENV),
            "$CARTELLA_MODELLI_ENV non impostata: percorso dei modelli sherpa-onnx scaricati per i test @modelli " +
                "(pyannote segmentation-3.0, WeSpeaker embedding, i test_wavs di qwen3-asr)",
        )
        return Path.of(percorso)
    }

    private companion object {
        const val CARTELLA_MODELLI_ENV = "SNASTRO_MODELLI_R1_DIR"
        const val NUMERO_THREAD = 2
    }
}
