package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.VoceRef
import snastro.parlanti.dominio.ErroreParlanti

/** A Voce of an Incontro as read in ONE of its Parti: the Parte's [registrazione] and the Voce's [voce] there. */
internal class VoceNellaParte(val registrazione: RegistrazioneVista, val voce: VoceVista)

/**
 * VoceRef → its Parti (ADR 0033 §4.1, D-0031): [LettoreRegistrazione.parti] of the Voce's Incontro, then each
 * Parte's [LettoreVoci.voci] (whose VoceRefs carry the Incontro), kept where [voceRef] speaks. The Parti are
 * UNORDERED: when the Voce speaks in several, which one is read is unspecified until the ordered reads of wave 4
 * (INV-I17); every Incontro has one Parte until then. Unknown or ceased Incontro, or a Voce speaking in none of its
 * Parti → [ErroreParlanti.VoceNonTrovata]; no Parte with a Trascritto → [ErroreParlanti.TrascrittoNonTrovato].
 */
@Suppress("ReturnCount") // one guard clause per answer of the KDoc above — clearer than nesting
internal fun leggiVoceNellaParte(
    voceRef: VoceRef,
    registrazioni: LettoreRegistrazione,
    voci: LettoreVoci,
): Esito<VoceNellaParte> {
    val parti = registrazioni.parti(voceRef.incontroId) ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    val trascritte = parti.mapNotNull { parte -> voci.voci(parte)?.let { parte to it } }
    if (trascritte.isEmpty()) return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(parti.first()))
    for ((parte, vociDellaParte) in trascritte) {
        val voce = vociDellaParte.find { it.voceRef == voceRef } ?: continue
        val registrazione = registrazioni.registrazione(parte)
            ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(parte))
        return Esito.Ok(VoceNellaParte(registrazione, voce))
    }
    return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
}

/** [leggiVoceNellaParte] when only "found or not" matters (read-models). */
internal fun voceNellaParte(voceRef: VoceRef, registrazioni: LettoreRegistrazione, voci: LettoreVoci): VoceNellaParte? =
    (leggiVoceNellaParte(voceRef, registrazioni, voci) as? Esito.Ok)?.valore
