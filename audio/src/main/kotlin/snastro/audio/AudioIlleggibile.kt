package snastro.audio

import java.io.IOException
import java.nio.file.Path

/**
 * [file] is missing, empty, a directory, or FFmpeg cannot open it as media at all (corrupted or not
 * a media file). Distinct from [FormatoNonSupportato] (an openable file with no audio stream).
 */
public class AudioIlleggibile(public val file: Path, cause: Throwable? = null) :
    IOException("Audio illeggibile: $file", cause)
