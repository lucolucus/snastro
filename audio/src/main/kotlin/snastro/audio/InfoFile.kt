package snastro.audio

import java.time.LocalDate

/** What [SondaFfmpeg.sonda] learns from a source file: its duration and the file's own date. */
public data class InfoFile(val durataMs: Long, val modificatoIl: LocalDate)
