package snastro.trascrizione.adattatori.ml

import org.junit.jupiter.api.Tag
import snastro.audio.DecodificaFfmpeg
import snastro.audio.SondaFfmpeg
import snastro.kernel.CampioniAudio
import snastro.ml.ConfigSessione
import snastro.ml.MotoreSherpa
import snastro.trascrizione.applicazione.porte.Vad
import snastro.trascrizione.applicazione.porte.VadContratto
import java.nio.file.Files
import java.nio.file.Path

/**
 * [VadSilero] against the real Silero VAD (AC-255, ADR 0004/0013/0015/0016). Opt-in
 * (`@Tag("modelli")`, `./gradlew :trascrizione:adattatori:modelliTest`): needs the sherpa-onnx
 * natives (fetched by the `modelliTest` task) and an already-downloaded model — read from an
 * environment variable (or system property), never committed:
 * - [PROPRIETA_MODELLO] (`VAD_SILERO_MODELLO`): absolute path to `silero_vad.onnx`;
 * - [PROPRIETA_WAV_PARLATO] (`VAD_SILERO_WAV_PARLATO`): absolute path to a speech WAV shipped
 *   inside a sherpa model archive (e.g. `test_wavs/en.wav` of a Parakeet archive) — never a
 *   `sample/` recording. Decoded to 16 kHz mono through `:audio`'s real FFmpeg (ADR 0005), the
 *   same path a real source takes.
 */
@Tag("modelli")
class VadSileroTest : VadContratto() {
    override fun vad(): Vad = VadSilero(MotoreSherpa(), config())

    override fun parlato(): CampioniAudio = campioniDaWav(percorsoDi(PROPRIETA_WAV_PARLATO))

    private fun config() = ConfigSessione(
        percorsiModello = listOf(percorsoDi(PROPRIETA_MODELLO)),
        threadIntraOp = ConfigSessione.coreDiPrestazione(),
    )

    private fun campioniDaWav(sorgente: Path): CampioniAudio {
        val wav = Files.createTempFile("vad-silero-test-", ".wav")
        try {
            DecodificaFfmpeg().decodificaInWav(sorgente, wav)
            val durataMs = SondaFfmpeg().sonda(wav).durataMs
            return CampioniAudio(DecodificaFfmpeg().leggiCampioni(wav, 0, durataMs))
        } finally {
            Files.deleteIfExists(wav)
        }
    }

    private fun percorsoDi(nomeProprieta: String): Path {
        val valore = System.getenv(nomeProprieta) ?: System.getProperty(nomeProprieta)
        checkNotNull(valore) {
            "$nomeProprieta non impostata: esporta la variabile d'ambiente (o passa -D$nomeProprieta) con un " +
                "percorso assoluto prima di ./gradlew :trascrizione:adattatori:modelliTest (AC-255, mai committato)"
        }
        return Path.of(valore)
    }

    private companion object {
        const val PROPRIETA_MODELLO = "VAD_SILERO_MODELLO"
        const val PROPRIETA_WAV_PARLATO = "VAD_SILERO_WAV_PARLATO"
    }
}
