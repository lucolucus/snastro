package snastro.progetto.applicazione.porte

import java.time.LocalDate

/** What [SondaAudio] learns from a source file: its duration and its recording date ([dataFile], AC-364). */
public data class InfoAudio(val durataMs: Long, val dataFile: LocalDate)
