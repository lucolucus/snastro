package snastro.parlanti.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/** One Voce of an Incontro as read in ONE of its Parti: its [intervalli] there. */
internal data class VoceDellaParte(val voceRef: VoceRef, val intervalli: List<IntervalloMs>)

/**
 * The Voci speaking in the Parte [parte], in [LettoreVoci.voci] order — for the S3 panel of one Parte
 * (`IdentificazioneVoci`). Two reads per call: the Parte's Incontro through [LettoreRegistrazione] (boundary
 * `registrazione-incontro-id`), then its Voci. `null` iff [parte] is unknown or has no Trascritto: a transcribed
 * Parte always has at least one Voce.
 */
internal fun LettoreVoci.vociDellaParte(
    parte: RegistrazioneId,
    registrazioni: LettoreRegistrazione,
): List<VoceDellaParte>? {
    val incontroId = registrazioni.registrazione(parte)?.incontroId ?: return null
    return voci(incontroId)
        ?.mapNotNull { v -> v.intervalliPerParte[parte]?.let { VoceDellaParte(v.voceRef, it) } }
        ?.ifEmpty { null }
}
