package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Parlante

/**
 * Policy `ApplicaRevisione` ([INV-21], [INV-25], ADR 0012): reacts to a Trascrizione Revisione
 * (`VociUnite` / `VoceDivisa` / `SegmentoRiassegnato`) inside the SAME transaction as the publishing
 * command. It never imports those published events (`:parlanti:applicazione` may not depend on
 * `:trascrizione:applicazione`, `architecture.md` edges table): `abbonato-revisione-parlanti`
 * (`:parlanti:adattatori`) subscribes to them and translates each into the call below.
 *
 * STRUCTURAL part only (ADR 0012 Amendment (b) points 3-4): it never decodes nor extracts. A surviving
 * or changed attributed `Voce` KEEPS its print row here — stale by its `sorgente`, refreshed after
 * commit by `RiallineaImpronte`. Every rule goes through [Parlante] / [Attribuzione] (RC-1); this class
 * only orchestrates lookups and applies the [INV-25] cross-aggregate cessation of an `occasionale` left
 * without any Attribuzione. A `Parlante` referenced by a persisted `Attribuzione` but missing from
 * [ParlanteRepository] is a data-integrity fault (`check`), not an expected business error (ADR 0003).
 */
public class ApplicaRevisionePolitica(
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
    private val voci: LettoreVoci,
) {
    /**
     * `VociUnite`: [rimossa] disappears into [sopravvissuta]. If [rimossa] is unattributed nothing
     * changes ([sopravvissuta] keeps its row, now stale). If [sopravvissuta] has its OWN Attribuzione it
     * wins ([INV-21]): [rimossa]'s Attribuzione and prints go ([INV-25] may cessa its Parlante) — unless both belong
     * to the SAME Parlante, whose [rimossa] prints are re-keyed onto [sopravvissuta] for the Parti where it has none
     * (where both have one, [sopravvissuta]'s is kept). Otherwise
     * [sopravvissuta] INHERITS [rimossa]'s Parlante by re-keying (user decision 2026-09-23).
     */
    public fun applicaVociUnite(incontroId: IncontroId, sopravvissuta: VoceId, rimossa: VoceId): Esito<Unit> {
        val perRimossa = VoceRef(incontroId, rimossa)
        val perSopravvissuta = VoceRef(incontroId, sopravvissuta)
        val attribuzioneRimossa = attribuzioni.trova(perRimossa) ?: return Esito.Ok(Unit)
        val attribuzioneSopravvissuta = attribuzioni.trova(perSopravvissuta)
        return when {
            attribuzioneSopravvissuta == null -> eredita(attribuzioneRimossa, perSopravvissuta)
            attribuzioneSopravvissuta.parlanteId == attribuzioneRimossa.parlanteId ->
                fondiNelloStessoParlante(attribuzioneRimossa, perSopravvissuta)
            else -> rimuoviSePresente(perRimossa)
        }
    }

    /**
     * `VoceDivisa`: the new `Voce` starts without Attribuzione (no row exists for it) and [origine] keeps its
     * Attribuzione and prints (stale, refreshed after commit) — except the print of every Parte whose slice of
     * [origine] the split emptied: it has no source left, so it goes ([INV-21]).
     */
    public fun applicaVoceDivisa(incontroId: IncontroId, origine: VoceId): Esito<Unit> =
        rimuoviImprontePerParteSvuotate(VoceRef(incontroId, origine))

    /**
     * `SegmentoRiassegnato`: the source [da], if left without any Segmento ([daRimossa]), loses its
     * Attribuzione and prints; otherwise it keeps them (stale) — but the print of a Parte whose slice of [da] was
     * emptied goes ([INV-21]). The destination [a] either starts without
     * Attribuzione ([aNuova]) or keeps its own row (stale): nothing to do for it in-transaction.
     */
    @Suppress("UnusedParameter") // mirrors the SegmentoRiassegnato event 1:1 for abbonato-revisione-parlanti (AC-142)
    public fun applicaSegmentoRiassegnato(
        incontroId: IncontroId,
        da: VoceId,
        a: VoceId,
        daRimossa: Boolean,
        aNuova: Boolean,
    ): Esito<Unit> =
        VoceRef(incontroId, da).let { if (daRimossa) rimuoviSePresente(it) else rimuoviImprontePerParteSvuotate(it) }

    /**
     * [INV-21] the removed [voceRef] loses its Attribuzione and derived print, then [INV-25]: an `attivo
     * occasionale` left without any Attribuzione ceases to exist; a `ricorrente` (or a tombstone) is kept.
     */
    private fun rimuoviSePresente(voceRef: VoceRef): Esito<Unit> {
        val attribuzione = attribuzioni.trova(voceRef) ?: return Esito.Ok(Unit)
        attribuzioni.rimuovi(voceRef)
        val parlante = parlanteDi(attribuzione)
        parlante.rimuoviImpronta(voceRef) // the Voce ceased: no source in any Parte
        return parlanti.salva(parlante).mappa {
            if (parlante.attivo && parlante.occasionale && attribuzioni.diParlante(parlante.id).isEmpty()) {
                parlanti.rimuovi(parlante.id)
            }
        }
    }

    /**
     * [INV-21] `unire` inheritance: [daRimossa] is RE-KEYED to [perSopravvissuta] — the Attribuzione
     * ([Attribuzione.trasferisci], valid for an `eliminato` tombstone too) and the Parlante's print row
     * ([Parlante.riassegnaImpronte], keeping `sorgente`/`modello`: stale by construction; a no-op for an
     * `eliminato`, which has no print). The Parlante keeps an Attribuzione, so [INV-25] never fires here.
     */
    private fun eredita(daRimossa: Attribuzione, perSopravvissuta: VoceRef): Esito<Unit> {
        attribuzioni.rimuovi(daRimossa.voceRef)
        attribuzioni.salva(daRimossa.trasferisci(perSopravvissuta))
        val parlante = parlanteDi(daRimossa)
        check(!parlante.haImprontaDi(perSopravvissuta)) {
            "$perSopravvissuta ha un'impronta di ${parlante.id} ma nessuna Attribuzione"
        }
        parlante.riassegnaImpronte(da = daRimossa.voceRef, a = perSopravvissuta)
        return parlanti.salva(parlante)
    }

    /** [INV-21] `unire` of two Voci of the SAME Parlante: B's Attribuzione goes, its prints merge onto A. */
    private fun fondiNelloStessoParlante(daRimossa: Attribuzione, perSopravvissuta: VoceRef): Esito<Unit> {
        attribuzioni.rimuovi(daRimossa.voceRef)
        val parlante = parlanteDi(daRimossa)
        parlante.riassegnaImpronte(da = daRimossa.voceRef, a = perSopravvissuta)
        return parlanti.salva(parlante)
    }

    /**
     * [INV-21] a print whose (Voce, Parte) slice a Revisione emptied has no source left and goes (its FK would fail
     * the COMMIT, ADR 0034 §2). The surviving slices are read through [LettoreVoci] (the Revisione is already
     * applied in this unit); a Voce the reader no longer knows is handled by its own removal path.
     */
    private fun rimuoviImprontePerParteSvuotate(voceRef: VoceRef): Esito<Unit> {
        val attribuzione = attribuzioni.trova(voceRef)
        val parti = voci.voci(voceRef.incontroId)?.find { it.voceRef == voceRef }?.intervalliPerParte?.keys
        if (attribuzione == null || parti == null) return Esito.Ok(Unit)
        val parlante = parlanteDi(attribuzione)
        parlante.rimuoviImpronteSenzaFetta(voceRef, parti)
        return parlanti.salva(parlante)
    }

    private fun parlanteDi(attribuzione: Attribuzione): Parlante =
        checkNotNull(parlanti.trova(attribuzione.parlanteId)) {
            "Attribuzione(${attribuzione.voceRef}) punta al Parlante inesistente ${attribuzione.parlanteId}"
        }
}
