package snastro.audio

import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Opens [file] with FFmpeg and checks it has at least one audio stream. Shared by [SondaFfmpeg] and
 * [DecodificaFfmpeg] — the only two native-decoder entry points (RC-5: the caller closes the
 * returned grabber with [chiudi] once done; it never leaves `:audio`).
 *
 * @throws AudioIlleggibile [file] is missing, empty, a directory, or FFmpeg cannot open it as media.
 * @throws FormatoNonSupportato [file] opens fine but has no audio stream.
 */
internal fun apriGrabberAudio(file: Path): FFmpegFrameGrabber {
    validaFileLeggibile(file)
    val grabber = avviaGrabber(file)
    if (grabber.audioChannels <= 0) {
        chiudi(grabber)
        throw FormatoNonSupportato(file)
    }
    return grabber
}

private fun validaFileLeggibile(file: Path) {
    if (!Files.exists(file) || file.isDirectory() || Files.size(file) == 0L) {
        throw AudioIlleggibile(file)
    }
}

private fun avviaGrabber(file: Path): FFmpegFrameGrabber {
    val grabber = FFmpegFrameGrabber(file.toFile())
    try {
        grabber.start()
    } catch (e: org.bytedeco.javacv.FrameGrabber.Exception) {
        throw AudioIlleggibile(file, e)
    }
    return grabber
}

/** Releases a grabber opened by [apriGrabberAudio], tolerating a grabber that never fully started. */
internal fun chiudi(grabber: FFmpegFrameGrabber) {
    try {
        grabber.stop()
    } finally {
        grabber.release()
    }
}
