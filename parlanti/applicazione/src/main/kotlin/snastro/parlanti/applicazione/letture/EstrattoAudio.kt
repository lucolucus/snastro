package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.dominio.BUDGET_ESTRATTO_MS
import snastro.parlanti.dominio.MAX_INTERVALLI_ESTRATTO
import snastro.parlanti.dominio.selezionaIntervalli

/**
 * Read-model `EstrattoAudio` (AC-105..AC-107, ADR 0012 Amendment (b) point 1, [INV-I17]): the short excerpt a
 * Voce card plays, built from the Voce's CURRENT Segmenti intervals in ONE Parte through the ONE shared selection
 * rule [selezionaIntervalli] — never re-implemented here (RC-1). Always one file, one player load (ADR 0005).
 * Shared by `proposta`, `parlanti-del-progetto` and the S3 identification screen.
 */
public class EstrattoAudio(private val voci: LettoreVoci, private val registrazioni: LettoreRegistrazione) {
    /**
     * [INV-I17]: from the Parte where the Voce speaks MOST (summed interval durations); on a tie, the earlier Parte in
     * the Incontro's order ([LettoreRegistrazione.parti], [INV-I2]). AC-105/AC-106: `selezionaIntervalli` with
     * [BUDGET_ESTRATTO_MS] and [MAX_INTERVALLI_ESTRATTO]. AC-107: `null` when [voceRef] doesn't exist, its Incontro
     * is unknown or has no Trascritto (INV-5), or the Voce has no interval left in any Parte.
     */
    public fun estratto(voceRef: VoceRef): EstrattoRef? {
        val perParte = intervalliPerParte(voceRef) ?: return null
        return registrazioni.parti(voceRef.incontroId).orEmpty()
            .mapNotNull { parte -> perParte[parte.registrazioneId]?.takeIf { it.isNotEmpty() }?.let { parte to it } }
            .maxByOrNull { (_, intervalli) -> intervalli.sumOf { it.fineMs - it.inizioMs } } // the first max: earlier
            ?.let { (parte, intervalli) -> estrattoDa(parte.registrazioneId, intervalli) }
    }

    /**
     * [INV-I17] for a Candidato: the excerpt of [voceRef] from the Parte [parte] that sourced the chosen print.
     * `null` when the Voce doesn't exist or has no interval left in that Parte.
     */
    public fun estratto(voceRef: VoceRef, parte: RegistrazioneId): EstrattoRef? =
        intervalliPerParte(voceRef)?.get(parte)?.takeIf { it.isNotEmpty() }?.let { estrattoDa(parte, it) }

    private fun intervalliPerParte(voceRef: VoceRef): Map<RegistrazioneId, List<IntervalloMs>>? =
        voci.voci(voceRef.incontroId)?.find { it.voceRef == voceRef }?.intervalliPerParte

    private fun estrattoDa(parte: RegistrazioneId, intervalli: List<IntervalloMs>): EstrattoRef =
        EstrattoRef(parte, selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO))
}
