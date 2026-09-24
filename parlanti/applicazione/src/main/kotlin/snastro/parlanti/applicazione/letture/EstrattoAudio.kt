package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.dominio.BUDGET_ESTRATTO_MS
import snastro.parlanti.dominio.MAX_INTERVALLI_ESTRATTO
import snastro.parlanti.dominio.selezionaIntervalli

/**
 * Read-model `EstrattoAudio` (AC-105..AC-107, ADR 0012 Amendment (b) point 1): the short excerpt a
 * Voce card plays, built from the Voce's CURRENT Segmenti intervals through the ONE shared selection
 * rule [selezionaIntervalli] — never re-implemented here (RC-1). Shared by `proposta`,
 * `parlanti-del-progetto` and the S3 identification screen (rule 11: this block is built before them).
 */
public class EstrattoAudio(private val voci: LettoreVoci) {
    /**
     * AC-105/AC-106: `selezionaIntervalli` of the Voce's intervals with [BUDGET_ESTRATTO_MS] (10 000 ms)
     * and [MAX_INTERVALLI_ESTRATTO] (3). AC-107: `null` when [voceRef] doesn't exist, the Registrazione
     * has no Trascritto (INV-5, [LettoreVoci.voci] returns `null`), or the Voce has no interval left
     * (emptied by a Revisione) — the same "nothing to derive from" case `RiallineaImpronte` skips.
     */
    public fun estratto(voceRef: VoceRef): EstrattoRef? {
        val intervalli = voci.voci(voceRef.registrazioneId)
            ?.find { it.voceRef == voceRef }
            ?.intervalli
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val scelti = selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)
        return EstrattoRef(voceRef.registrazioneId, scelti)
    }
}
