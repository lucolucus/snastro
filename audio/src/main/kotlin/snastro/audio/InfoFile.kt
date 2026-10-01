package snastro.audio

import java.time.LocalDate
import java.time.LocalTime

/**
 * What [SondaFfmpeg.sonda] learns from a source file: its duration, its recording date (AC-364) and, when
 * the file says it (`moov/udta/date`, ADR 0040), the local [oraDiInizio] to the second; `null` otherwise.
 */
public data class InfoFile(val durataMs: Long, val dataRegistrazione: LocalDate, val oraDiInizio: LocalTime? = null)
