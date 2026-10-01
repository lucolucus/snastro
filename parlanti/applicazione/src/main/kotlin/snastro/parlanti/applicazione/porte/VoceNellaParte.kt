package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.dominio.ErroreParlanti

/** One Voce of an Incontro as read in ONE of its Parti: its [intervalli] there. */
internal data class VoceDellaParte(val voceRef: VoceRef, val intervalli: List<IntervalloMs>)

/** A Voce of an Incontro as read in ONE of its Parti: the Parte's [registrazione] and the Voce's [voce] there. */
internal class VoceNellaParte(val registrazione: RegistrazioneVista, val voce: VoceDellaParte)

/**
 * VoceRef → one of its Parti (ADR 0033 §4.1): the Voce from [LettoreVoci.voci] of its Incontro, read in the first
 * Parte of [LettoreRegistrazione.parti] where it speaks. Choosing the Parte where it speaks MOST ([INV-I17]) belongs
 * to the Parlanti read-models of wave 5; every Incontro has one Parte until the I2 import. Unknown or ceased Incontro,
 * or a Voce speaking in none of its Parti → [ErroreParlanti.VoceNonTrovata]; no Parte with a Trascritto →
 * [ErroreParlanti.TrascrittoNonTrovato].
 */
@Suppress("ReturnCount") // one guard clause per answer of the KDoc above — clearer than nesting
internal fun leggiVoceNellaParte(
    voceRef: VoceRef,
    registrazioni: LettoreRegistrazione,
    voci: LettoreVoci,
): Esito<VoceNellaParte> {
    val parti = registrazioni.parti(voceRef.incontroId) ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    val vociDellIncontro = voci.voci(voceRef.incontroId)
        ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(parti.first().registrazioneId))
    val voce = vociDellIncontro.find { it.voceRef == voceRef }
        ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    for (parte in parti) {
        val intervalli = voce.intervalliPerParte[parte.registrazioneId] ?: continue
        val registrazione = registrazioni.registrazione(parte.registrazioneId)
            ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(parte.registrazioneId))
        return Esito.Ok(VoceNellaParte(registrazione, VoceDellaParte(voceRef, intervalli)))
    }
    return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
}

/** [leggiVoceNellaParte] when only "found or not" matters (read-models). */
internal fun voceNellaParte(voceRef: VoceRef, registrazioni: LettoreRegistrazione, voci: LettoreVoci): VoceNellaParte? =
    (leggiVoceNellaParte(voceRef, registrazioni, voci) as? Esito.Ok)?.valore

/**
 * The Voci speaking in the Parte [parte], in [LettoreVoci.voci] order — for the per-Parte callers that keep their
 * shape until the Incontro-wide read-models of wave 5. Its Incontro is read through [LettoreRegistrazione]
 * (boundary `registrazione-incontro-id`). `null` iff [parte] is unknown or has no Trascritto: a transcribed Parte
 * always has at least one Voce.
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

/** The Segmenti of the Parte [parte] in [LettoreVoci.segmenti] order; `null` exactly as [vociDellaParte]. */
internal fun LettoreVoci.segmentiDellaParte(
    parte: RegistrazioneId,
    registrazioni: LettoreRegistrazione,
): List<SegmentoDiVoce>? {
    val incontroId = registrazioni.registrazione(parte)?.incontroId ?: return null
    return segmenti(incontroId)?.filter { it.segmento.registrazioneId == parte }?.ifEmpty { null }
}
