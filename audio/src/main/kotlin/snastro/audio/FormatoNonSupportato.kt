package snastro.audio

import java.io.IOException
import java.nio.file.Path

/** [file] opens fine as a known container/format, but FFmpeg finds no audio stream in it. */
public class FormatoNonSupportato(public val file: Path) : IOException("Formato audio non supportato: $file")
