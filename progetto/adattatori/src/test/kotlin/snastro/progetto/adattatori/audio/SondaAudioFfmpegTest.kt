package snastro.progetto.adattatori.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.progetto.applicazione.porte.SondaAudioContratto
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real against
 * [SondaAudioFfmpeg] (AC-147). Exercising it opens real native FFmpeg for most of the contract's
 * cases (everything but the guard clauses `SondaFfmpeg` itself resolves before touching a native
 * grabber), so the WHOLE class is `@Tag("modelli")` — opt-in, excluded from `check`, same split
 * `audio-ffmpeg` uses for its own `SondaFfmpegTest`. No committed sample (the profile forbids one):
 * fixtures are a synthetic silent WAV ([scriviWavSintetico], the exact shape `:audio` itself writes
 * — [snastro.audio.SondaFfmpeg] only needs a valid container + audio stream, not real content) and
 * a 1x1 PNG written by the JDK's own encoder, exactly as `SondaFfmpegTest` does.
 */
@Tag("modelli")
class SondaAudioFfmpegTest : SondaAudioContratto() {

    @TempDir
    lateinit var radice: Path

    override fun ambiente(): Ambiente = AmbienteFfmpeg(radice)

    private class AmbienteFfmpeg(radice: Path) : Ambiente {
        override val sonda: SondaAudio = SondaAudioFfmpeg()

        override val fileLeggibile: String = radice.resolve("sintetico.wav")
            .also { scriviWavSintetico(it, durataMs = 1_500) }
            .toString()

        override val dataDelFileLeggibile: LocalDate = Files
            .getLastModifiedTime(Path.of(fileLeggibile))
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        override val fileIlleggibile: String = radice.resolve("corrotto.m4a")
            .also { Files.write(it, ByteArray(64) { i -> i.toByte() }) }
            .toString()

        override val fileVuoto: String = radice.resolve("vuoto.m4a").also { Files.createFile(it) }.toString()

        override val cartella: String = radice.resolve("una-cartella").also { Files.createDirectories(it) }.toString()

        override val fileInesistente: String = radice.resolve("assente.m4a").toString()

        override val fileFormatoNonSupportato: String = radice.resolve("senza-audio.png")
            .also { scriviImmaginePng(it) }
            .toString()
    }
}

/** A minimal 16 kHz mono 16-bit PCM WAV of [durataMs] of silence — content is irrelevant to a probe. */
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

private fun scriviImmaginePng(destinazione: Path) {
    val immagine = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
    javax.imageio.ImageIO.write(immagine, "png", destinazione.toFile())
}
