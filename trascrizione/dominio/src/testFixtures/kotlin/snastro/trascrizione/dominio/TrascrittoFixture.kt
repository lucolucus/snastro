package snastro.trascrizione.dominio

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId

/** Duration of the fixture `Registrazione`: large enough for every fixture turn. */
public const val DURATA_TRASCRITTO_MS: Long = 600_000

/** One diarized turn as the pipeline hands it to [Trascritto.crea]. */
public fun unSegmentoIniziale(
    voceIndice: Int,
    inizioMs: Long,
    fineMs: Long = inizioMs + 1_000,
    testo: String = "testo $voceIndice@$inizioMs",
): SegmentoIniziale = SegmentoIniziale(voceIndice, IntervalloMs(inizioMs, fineMs), testo)

/**
 * A [Trascritto] created through [Trascritto.crea] (never `ricostituisci`, CR-15) with [voci] Voci
 * speaking in turns of 1 s: turn `j` of diarizer voice `v` starts at `(j * voci + v) * 1000` ms.
 * Hence Voce `v + 1` owns Segmenti `j * voci + v + 1` for `j` in `0 until segmentiPerVoce`.
 */
public fun unTrascritto(
    voci: Int = 2,
    segmentiPerVoce: Int = 3,
    registrazioneId: RegistrazioneId = RegistrazioneId("id-1"),
): Trascritto {
    val turni = (0 until segmentiPerVoce).flatMap { j ->
        (0 until voci).map { v -> unSegmentoIniziale(voceIndice = v, inizioMs = (j * voci + v) * 1_000L) }
    }
    val esito = Trascritto.crea(registrazioneId, DURATA_TRASCRITTO_MS, turni)
    check(esito is Esito.Ok) { "fixture non valida: $esito" }
    return esito.valore.aggregato
}
