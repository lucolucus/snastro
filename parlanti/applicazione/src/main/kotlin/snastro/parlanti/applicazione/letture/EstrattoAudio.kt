package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.voceNellaParte
import snastro.parlanti.dominio.BUDGET_ESTRATTO_MS
import snastro.parlanti.dominio.MAX_INTERVALLI_ESTRATTO
import snastro.parlanti.dominio.selezionaIntervalli

/**
 * Read-model `EstrattoAudio` (AC-105..AC-107, ADR 0012 Amendment (b) point 1): the short excerpt a
 * Voce card plays, built from the Voce's CURRENT Segmenti intervals through the ONE shared selection
 * rule [selezionaIntervalli] — never re-implemented here (RC-1). Shared by `proposta`,
 * `parlanti-del-progetto` and the S3 identification screen (rule 11: this block is built before them).
 */
public class EstrattoAudio(private val voci: LettoreVoci, private val registrazioni: LettoreRegistrazione) {
    /**
     * AC-105/AC-106: `selezionaIntervalli` of the Voce's intervals with [BUDGET_ESTRATTO_MS] (10 000 ms)
     * and [MAX_INTERVALLI_ESTRATTO] (3). AC-107: `null` when [voceRef] doesn't exist, the Registrazione
     * has no Trascritto (INV-5, [LettoreVoci.voci] returns `null`), or the Voce has no interval left
     * (emptied by a Revisione) — the same "nothing to derive from" case `RiallineaImpronte` skips.
     */
    @Suppress("ReturnCount") // the two "nothing to derive from" guard clauses of AC-107 — clearer than nesting
    public fun estratto(voceRef: VoceRef): EstrattoRef? {
        // ADR 0033 §4.1: the extract is taken from ONE Parte the Voce speaks in (INV-I17).
        val letta = voceNellaParte(voceRef, registrazioni, voci) ?: return null
        val intervalli = letta.voce.intervalli.takeIf { it.isNotEmpty() } ?: return null
        val scelti = selezionaIntervalli(intervalli, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)
        return EstrattoRef(letta.registrazione.registrazioneId, scelti)
    }
}
