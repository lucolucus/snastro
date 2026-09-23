package snastro.audio

import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.nio.ShortBuffer
import java.nio.file.Path

/**
 * Writes a short silent AAC `.m4a` whose container carries the `creation_time` tag [creationTime]
 * (FFmpeg's mov muxer stores it in `mvhd`, like a phone recorder does) — encoded with bytedeco FFmpeg
 * at test time, so no audio sample is ever committed (AC-364 real-probe tests, `@Tag("modelli")`).
 */
internal fun scriviM4aSintetico(destinazione: Path, creationTime: String, durataMs: Long = 500) {
    val recorder = FFmpegFrameRecorder(destinazione.toFile(), 1)
    try {
        recorder.format = "ipod"
        recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
        recorder.sampleRate = FREQUENZA_HZ
        recorder.audioBitrate = BITRATE
        recorder.setMetadata("creation_time", creationTime)
        recorder.start()
        val campioni = ShortArray((FREQUENZA_HZ * durataMs / MS_PER_SECONDO).toInt())
        recorder.recordSamples(FREQUENZA_HZ, 1, ShortBuffer.wrap(campioni))
        recorder.stop()
    } finally {
        recorder.release()
    }
}

private const val FREQUENZA_HZ = 44_100
private const val BITRATE = 64_000
private const val MS_PER_SECONDO = 1_000L
