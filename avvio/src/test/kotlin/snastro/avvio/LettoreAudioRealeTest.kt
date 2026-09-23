package snastro.avvio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioContratto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D2 (real-on-real): [LettoreAudioReale] over the REAL [snastro.audio.RiproduttoreWav] (native
 * `javax.sound` line) and [snastro.audio.DecodificaFfmpeg] (native FFmpeg) — `@Tag("modelli")` like
 * every other real audio adapter test in this codebase (`:audio`'s own `RiproduttoreWavTest`/
 * `DecodificaFfmpegTest`): excluded from the default `check` gate (`excludeTags("modelli", "render")`,
 * `ConventionUtils.kt`), run via the opt-in `modelliTest`.
 */
@Tag("modelli")
class LettoreAudioRealeTest : LettoreAudioContratto() {
    @TempDir
    lateinit var cartellaProgetto: Path

    private val registrazioneId = RegistrazioneId("id-1")

    // LettoreAudioContratto also exercises a SECOND id ("id-2", `riproduciDa`/`riproduciEstratto`
    // "sostituisce" tests): both need a real, existing source for `con()`'s adapter to actually play.
    private val idConSorgente = setOf(RegistrazioneId("id-1"), RegistrazioneId("id-2"))

    override fun con(): LettoreAudio {
        idConSorgente.forEach { id ->
            scriviWavSintetico(cartellaProgetto.resolve("audio/${id.valore}.wav"), durataMs = 2_000)
        }
        return LettoreAudioReale(
            cartellaProgetto,
            riferimentoAudioDi = { id ->
                if (id in idConSorgente) RiferimentoAudio("audio/${id.valore}.wav") else null
            },
        )
    }

    @Test
    fun `AC-241 senza sorgente riporta non disponibile`() {
        val lettore = LettoreAudioReale(cartellaProgetto, riferimentoAudioDi = { null })
        assertFalse(lettore.disponibile(RegistrazioneId("sconosciuto")))
    }

    @Test
    fun `AC-241 il lettore ricostruisce il WAV derivato mancante dalla sorgente`() {
        val lettore = con()
        val derivato = cartellaProgetto.resolve("cache/audio/${registrazioneId.valore}.wav")
        assertFalse(Files.exists(derivato), "il derivato non deve esistere prima della prima riproduzione")

        lettore.riproduciDa(registrazioneId, 0)

        assertTrue(Files.exists(derivato), "riproduciDa deve ricostruire il WAV derivato mancante")
    }

    @Test
    fun `AC-241 il lettore non ricostruisce un WAV derivato gia presente`() {
        val lettore = con()
        val derivato = cartellaProgetto.resolve("cache/audio/${registrazioneId.valore}.wav")
        lettore.riproduciDa(registrazioneId, 0)
        val ultimaModificaDopoPrimaRiproduzione = Files.getLastModifiedTime(derivato)

        lettore.pausa()
        lettore.riproduciDa(registrazioneId, 100)

        assertEquals(
            ultimaModificaDopoPrimaRiproduzione,
            Files.getLastModifiedTime(derivato),
            "il WAV derivato non doveva essere riscritto",
        )
    }

    @Test
    fun `M2 quando una riproduzione reale arriva alla fine da sola, stato torna a inRiproduzione false`() {
        val lettore = con()

        lettore.riproduciDa(registrazioneId, 0)
        assertTrue(lettore.stato.value.inRiproduzione, "la riproduzione deve partire")

        attendi(timeoutMs = DURATA_WAV_PROVA_MS * 5) { !lettore.stato.value.inRiproduzione }
    }

    private fun attendi(timeoutMs: Long, condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < scadenza && !condizione()) Thread.sleep(20)
        assertTrue(condizione(), "condizione non soddisfatta entro ${timeoutMs}ms")
    }

    private companion object {
        const val DURATA_WAV_PROVA_MS = 2_000L
    }
}
