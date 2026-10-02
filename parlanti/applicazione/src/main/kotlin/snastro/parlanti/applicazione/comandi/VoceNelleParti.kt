package snastro.parlanti.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.SorgenteImpronta
import java.time.LocalDate

/** A Voce as it speaks in ONE Parte: the Parte and the Voce's [intervalli] there (never empty). */
internal class VoceInParte(val parte: RegistrazioneId, val intervalli: List<IntervalloMs>) {
    val sorgente: SorgenteImpronta = SorgenteImpronta.di(intervalli)
}

/**
 * A Voce of an Incontro read in EVERY Parte where it speaks ([parti], in the Incontro's order, at least one) —
 * the input of the per-Parte prints ([INV-I8]). [dataDelIncontro] is the FIRST Parte's date of the Incontro, whether
 * or not the Voce speaks there ([INV-19]).
 */
internal class VoceNelleParti(
    val progettoId: ProgettoId,
    val dataDelIncontro: LocalDate,
    val parti: List<VoceInParte>,
) {
    /** What each print was extracted from: equal before and inside the transaction ⇔ the Voce did not change. */
    fun sorgenti(): List<Pair<RegistrazioneId, String>> = parti.map { it.parte to it.sorgente.chiave }
}

/**
 * VoceRef → its Parti (ADR 0033 §4.1, ADR 0035 §6): unknown or ceased Incontro, or a Voce of no Parte →
 * [ErroreParlanti.VoceNonTrovata]; no Parte with a Trascritto → [ErroreParlanti.TrascrittoNonTrovato];
 * a Parte the catalogue no longer knows → [ErroreParlanti.TrascrittoNonTrovato] of that Parte.
 */
@Suppress("ReturnCount") // one guard clause per answer of the KDoc above, as `leggiVoceNellaParte`
internal fun leggiVoceNelleParti(
    voceRef: VoceRef,
    registrazioni: LettoreRegistrazione,
    voci: LettoreVoci,
): Esito<VoceNelleParti> {
    val partiDelCatalogo = registrazioni.parti(voceRef.incontroId)
        ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    val vociDellIncontro = voci.voci(voceRef.incontroId)
        ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(partiDelCatalogo.first().registrazioneId))
    val voce = vociDellIncontro.find { it.voceRef == voceRef }
        ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    val parti = partiDelCatalogo.mapNotNull { p ->
        val intervalli = voce.intervalliPerParte[p.registrazioneId]?.takeIf { it.isNotEmpty() }
        intervalli?.let { VoceInParte(p.registrazioneId, it) }
    }
    val prima = parti.firstOrNull() ?: return Esito.Errore(ErroreParlanti.VoceNonTrovata(voceRef))
    val registrazione = registrazioni.registrazione(prima.parte)
        ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(prima.parte))
    return Esito.Ok(VoceNelleParti(registrazione.progettoId, partiDelCatalogo.first().dataRegistrazione, parti))
}
