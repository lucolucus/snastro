package snastro.avvio.r1

import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.Vad

/**
 * The three ML ports of the pipeline, as chosen by [adattatori] (the single wiring point). The
 * `Allineatore` is not here: it is always `AllineatorePerTurno(riconoscitore, vad)` (ADR 0015).
 */
internal class AdattatoriMl(
    val diarizzatore: Diarizzatore,
    val riconoscitore: RiconoscitoreParlato,
    val vad: Vad,
)
