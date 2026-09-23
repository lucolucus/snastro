package snastro.audio

import java.time.LocalDate

/** What [SondaFfmpeg.sonda] learns from a source file: its duration and its recording date (AC-364). */
public data class InfoFile(val durataMs: Long, val dataRegistrazione: LocalDate)
