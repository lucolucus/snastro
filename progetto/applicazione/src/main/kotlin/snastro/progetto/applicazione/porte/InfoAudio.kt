package snastro.progetto.applicazione.porte

import java.time.LocalDate
import java.time.LocalTime

/**
 * What [SondaAudio] learns from a source file: its duration, its recording date ([dataFile], AC-364) and,
 * when the file says it (ADR 0040), the local time the recording started ([oraDiInizio], to the second).
 */
public data class InfoAudio(val durataMs: Long, val dataFile: LocalDate, val oraDiInizio: LocalTime? = null)
