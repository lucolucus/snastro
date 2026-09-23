package snastro.avvio

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.io.TempDir
import snastro.audio.RiproduttoreWav
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M2: [RiproduttoreWav] never pushes an end-of-playback event — [LettoreAudioReale.stato] must still
 * fall back to `inRiproduzione = false` once [RiproduttoreWav.inRiproduzione] reports the playback
 * reached ITS OWN end (not only after an explicit `pausa`). A fake `RiproduttoreWav` (MockK, RC-9:
 * verifies a poll interaction only — no hand-written recording fake would make sense for it, it is
 * not a port) drives the scenario deterministically, without touching the real audio line — the
 * REAL end-to-end case (tiny synthetic WAV, real playback) lives in [LettoreAudioRealeTest]
 * (`@Tag("modelli")`). This class does NOT extend a `*Contratto` — `io.mockk` is off-limits there
 * (CR-17).
 */
class LettoreAudioRealeSorveglianzaTest {
    @TempDir
    lateinit var cartella: Path

    private val id = RegistrazioneId("id-1")

    @Test
    fun `M2 quando la riproduzione reale finisce da sola, stato torna a inRiproduzione false`() {
        Files.createDirectories(cartella.resolve("audio"))
        Files.write(cartella.resolve("audio/${id.valore}.wav"), byteArrayOf(0)) // solo per l'esistenza
        // Il derivato e' gia' presente: mai decodificato (nessun bisogno di un DecodificaFfmpeg reale/finto).
        Files.createDirectories(cartella.resolve("cache/audio"))
        Files.write(cartella.resolve("cache/audio/${id.valore}.wav"), byteArrayOf(0))

        val riproduttoreFinto = mockk<RiproduttoreWav>(relaxed = true)
        every { riproduttoreFinto.inRiproduzione() } returnsMany listOf(true, true, false)
        val lettore = LettoreAudioReale(
            cartella,
            riferimentoAudioDi = { RiferimentoAudio("audio/${id.valore}.wav") },
            riproduttore = riproduttoreFinto,
        )

        lettore.riproduciDa(id, 0)
        assertTrue(lettore.stato.value.inRiproduzione)

        attendi { !lettore.stato.value.inRiproduzione }
    }

    private fun attendi(timeoutMs: Long = 2_000, condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < scadenza && !condizione()) Thread.sleep(10)
        assertTrue(condizione(), "condizione non soddisfatta entro ${timeoutMs}ms")
    }
}
