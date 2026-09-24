package snastro.avvio.r1

import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.Vad

/**
 * The three ML ports of the pipeline, as chosen by [SelezioneAdattatoriMl] (the single wiring point).
 * The `Allineatore` is not here: it is always `AllineatorePerTurno(riconoscitore, vad)` (ADR 0015).
 * [rilasciaDopoElaborazione] frees what an adapter keeps loaded across calls (the ASR model, ADR 0004
 * "released at the end of the Elaborazione", AC-388): the composition calls it once per Elaborazione,
 * when it terminates ([SegnalatoreFaseConRilascio]); the next run reloads lazily.
 */
internal class AdattatoriMl(
    val diarizzatore: Diarizzatore,
    val riconoscitore: RiconoscitoreParlato,
    val vad: Vad,
    val rilasciaDopoElaborazione: () -> Unit = {},
)
