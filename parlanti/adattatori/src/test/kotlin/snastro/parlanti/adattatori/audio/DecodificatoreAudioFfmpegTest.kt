package snastro.parlanti.adattatori.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioContratto
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real against
 * [DecodificatoreAudioFfmpeg] (AC-151). Exercising it opens real native FFmpeg for decode, so the
 * whole class is `@Tag("modelli")` — opt-in, excluded from `check`. No committed sample (the profile
 * forbids one): [registrazione]'s source is a synthetic silent WAV written at test time
 * ([scriviWavSintetico], the exact shape `:audio` itself writes and decodes in its own
 * `@Tag("modelli")` tests, `audio/src/test/kotlin/snastro/audio/DecodificaFfmpegTest.kt`) — FFmpeg
 * still does real work opening/resampling/re-muxing it, the same path a real source takes.
 */
@Tag("modelli")
class DecodificatoreAudioFfmpegTest : DecodificatoreAudioContratto() {

    @TempDir
    lateinit var cartellaProgetto: Path

    override fun decodificatore(): DecodificatoreAudio = DecodificatoreAudioFfmpeg(cartellaProgetto)

    // Named exactly like the production convention (audio/<registrazioneId>.<ext>, ADR 0010) so
    // AC-152 below can find it again by id after the derived WAV is deleted.
    override val registrazione: RegistrazioneId by lazy {
        val audio = cartellaProgetto.resolve("audio").also(Files::createDirectories)
        val durataMs = DecodificatoreAudioContratto.DURATA_MINIMA_MS
        scriviWavSintetico(audio.resolve("${REGISTRAZIONE.valore}.wav"), durataMs)
        REGISTRAZIONE
    }

    @Test
    fun `AC-152 un WAV derivato mancante viene ricostruito dalla sorgente copiata`() {
        val intervallo = IntervalloMs(0, 100)
        val primaCampioni = decodificatore().campioni(registrazione, listOf(intervallo)).campioni
        val wavDerivato = cartellaProgetto.resolve("cache/audio/${registrazione.valore}.wav")
        assertTrue(Files.exists(wavDerivato), "il WAV derivato deve esistere dopo la prima campioni()")

        Files.delete(wavDerivato)

        // Una nuova istanza: non ha memoria di quanto fatto prima, eppure ricostruisce.
        val dopoLaRicostruzione = decodificatore().campioni(registrazione, listOf(intervallo)).campioni

        assertTrue(Files.exists(wavDerivato), "il WAV derivato deve essere ricostruito dalla sorgente")
        assertContentEquals(primaCampioni, dopoLaRicostruzione)
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}

/** A minimal 16 kHz mono 16-bit PCM WAV of [durataMs] of silence — the exact shape `:audio` writes. */
private fun scriviWavSintetico(destinazione: Path, durataMs: Long) {
    val campioniPerMs = 16
    val dati = ByteArray((durataMs * campioniPerMs).toInt() * 2) // silenzio: PCM a zero
    RandomAccessFile(destinazione.toFile(), "rw").use { raf ->
        raf.setLength(0)
        raf.write(intestazioneWavMinimale(dati.size.toLong()))
        raf.write(dati)
    }
}

private fun intestazioneWavMinimale(dimensioneDati: Long): ByteArray {
    val frequenzaCampionamento = 16_000
    val bytePerCampione = 2 // mono, 16 bit
    val byteRate = frequenzaCampionamento * bytePerCampione
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36 + dimensioneDati).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1) // PCM
        putShort(1) // mono
        putInt(frequenzaCampionamento)
        putInt(byteRate)
        putShort(bytePerCampione.toShort())
        putShort((bytePerCampione * java.lang.Byte.SIZE).toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dimensioneDati.toInt())
    }.array()
}
