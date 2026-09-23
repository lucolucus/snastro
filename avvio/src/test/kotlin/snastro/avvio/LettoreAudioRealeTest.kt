package snastro.avvio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioContratto
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
}

/** Writes a tiny synthetic 16 kHz mono 16-bit PCM WAV (silence) — never a committed sample. */
private fun scriviWavSintetico(destinazione: Path, durataMs: Long) {
    val frequenzaHz = 16_000
    val bytePerCampione = 2
    val campioni = (durataMs * frequenzaHz / 1_000).toInt()
    val dati = ByteArray(campioni * bytePerCampione)
    destinazione.parent?.let(Files::createDirectories)
    RandomAccessFile(destinazione.toFile(), "rw").use { raf ->
        raf.setLength(0)
        raf.write(intestazioneWavProva(dati.size.toLong(), frequenzaHz, bytePerCampione))
        raf.write(dati)
    }
}

private fun intestazioneWavProva(dataSize: Long, frequenzaHz: Int, bytePerCampione: Int): ByteArray {
    val byteRate = frequenzaHz * bytePerCampione
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36 + dataSize).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1) // PCM
        putShort(1) // mono
        putInt(frequenzaHz)
        putInt(byteRate)
        putShort(bytePerCampione.toShort())
        putShort((bytePerCampione * 8).toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataSize.toInt())
    }.array()
}
