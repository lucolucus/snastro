package snastro.trascrizione.dominio

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi

/** Duration of the fixture `Registrazione`: large enough for every fixture turn. */
public const val DURATA_TRASCRITTO_MS: Long = 600_000

/** One diarized turn as the pipeline hands it to [VociDellIncontro.completaParte]. */
public fun unSegmentoIniziale(
    voceIndice: Int,
    inizioMs: Long,
    fineMs: Long = inizioMs + 1_000,
    testo: String = "testo $voceIndice@$inizioMs",
): SegmentoIniziale = SegmentoIniziale(voceIndice, IntervalloMs(inizioMs, fineMs), testo)

/**
 * The root of [incontroId] whose ONLY Parte [registrazioneId] is transcribed (first completion, never `ricostituisci`,
 * CR-15) with [voci] Voci speaking in turns of 1 s: turn `j` of diarizer voice `v` starts at `(j * voci + v) * 1000`
 * ms. Hence Voce `v + 1` owns Segmenti `j * voci + v + 1` for `j` in `0 until segmentiPerVoce`.
 */
public fun unaRadice(
    voci: Int = 2,
    segmentiPerVoce: Int = 3,
    registrazioneId: RegistrazioneId = RegistrazioneId("id-1"),
    incontroId: IncontroId = unIncontroDi(registrazioneId),
): VociDellIncontro {
    val turni = (0 until segmentiPerVoce).flatMap { j ->
        (0 until voci).map { v -> unSegmentoIniziale(voceIndice = v, inizioMs = (j * voci + v) * 1_000L) }
    }
    val radice = VociDellIncontro.crea(incontroId)
    val esito = radice.completaParte(registrazioneId, turni, DURATA_TRASCRITTO_MS)
    check(esito is Esito.Ok) { "fixture non valida: $esito" }
    return radice
}

/** The Trascritto (a read copy) of the one Parte of [unaRadice] built with the same arguments. */
public fun unTrascritto(
    voci: Int = 2,
    segmentiPerVoce: Int = 3,
    registrazioneId: RegistrazioneId = RegistrazioneId("id-1"),
    incontroId: IncontroId = unIncontroDi(registrazioneId),
): Trascritto = checkNotNull(unaRadice(voci, segmentiPerVoce, registrazioneId, incontroId).trascritto(registrazioneId))


/** The root of [incontroId] whose ONLY Parte [registrazioneId] is transcribed from [turni] (first completion). */
public fun unaRadiceDa(
    registrazioneId: RegistrazioneId,
    incontroId: IncontroId,
    durataMs: Long,
    turni: List<SegmentoIniziale>,
): VociDellIncontro {
    val radice = VociDellIncontro.crea(incontroId)
    val esito = radice.completaParte(registrazioneId, turni, durataMs)
    check(esito is Esito.Ok) { "fixture non valida: $esito" }
    return radice
}
