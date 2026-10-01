package snastro.audio

import java.time.LocalDate
import java.time.LocalTime

/** The recording date and, when the source says it, the local time the recording started (ADR 0040). */
internal data class DataEOra(val data: LocalDate, val ora: LocalTime?)
