package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
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
) {
    /**
     * `VociUnite`: [rimossa] disappears into [sopravvissuta]. If [rimossa] is unattributed nothing
     * changes ([sopravvissuta] keeps its row, now stale). If [sopravvissuta] has its OWN Attribuzione it
     * wins ([INV-21]): [rimossa]'s Attribuzione and row go ([INV-25] may cessa its Parlante). Otherwise
     * [sopravvissuta] INHERITS [rimossa]'s Parlante by re-keying (user decision 2026-09-23).
     */
    public fun applicaVociUnite(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId): Esito<Unit> {
        val perRimossa = VoceRef(registrazioneId, rimossa)
        val perSopravvissuta = VoceRef(registrazioneId, sopravvissuta)
        val attribuzioneRimossa = attribuzioni.trova(perRimossa) ?: return Esito.Ok(Unit)
        return if (attribuzioni.trova(perSopravvissuta) != null) {
            rimuoviSePresente(perRimossa)
        } else {
            eredita(attribuzioneRimossa, perSopravvissuta)
        }
    }

    /**
     * `VoceDivisa`: structurally nothing to do — the new `Voce` starts without Attribuzione (no row
     * exists for it) and [origine] keeps its Attribuzione and print row (stale, refreshed after commit).
     */
    @Suppress("UnusedParameter") // mirrors the VoceDivisa event 1:1 for abbonato-revisione-parlanti (AC-142)
    public fun applicaVoceDivisa(registrazioneId: RegistrazioneId, origine: VoceId): Esito<Unit> = Esito.Ok(Unit)

    /**
     * `SegmentoRiassegnato`: the source [da], if left without any Segmento ([daRimossa]), loses its
     * Attribuzione and print; otherwise it keeps them (stale). The destination [a] either starts without
     * Attribuzione ([aNuova]) or keeps its own row (stale): nothing to do for it in-transaction.
     */
    @Suppress("UnusedParameter") // mirrors the SegmentoRiassegnato event 1:1 for abbonato-revisione-parlanti (AC-142)
    public fun applicaSegmentoRiassegnato(
        registrazioneId: RegistrazioneId,
        da: VoceId,
        a: VoceId,
        daRimossa: Boolean,
        aNuova: Boolean,
    ): Esito<Unit> = if (daRimossa) rimuoviSePresente(VoceRef(registrazioneId, da)) else Esito.Ok(Unit)

    /**
     * [INV-21] the removed [voceRef] loses its Attribuzione and derived print, then [INV-25]: an `attivo
     * occasionale` left without any Attribuzione ceases to exist; a `ricorrente` (or a tombstone) is kept.
     */
    private fun rimuoviSePresente(voceRef: VoceRef): Esito<Unit> {
        val attribuzione = attribuzioni.trova(voceRef) ?: return Esito.Ok(Unit)
        attribuzioni.rimuovi(voceRef)
        val parlante = parlanteDi(attribuzione)
        parlante.rimuoviImpronta(voceRef)
        return parlanti.salva(parlante).mappa {
            if (parlante.attivo && parlante.occasionale && attribuzioni.diParlante(parlante.id).isEmpty()) {
                parlanti.rimuovi(parlante.id)
            }
        }
    }

    /**
     * [INV-21] `unire` inheritance: [daRimossa] is RE-KEYED to [perSopravvissuta] — the Attribuzione
     * ([Attribuzione.trasferisci], valid for an `eliminato` tombstone too) and the Parlante's print row
     * ([Parlante.trasferisciImpronta], keeping `sorgente`/`modello`: stale by construction; a no-op for an
     * `eliminato`, which has no print). The Parlante keeps an Attribuzione, so [INV-25] never fires here.
     */
    private fun eredita(daRimossa: Attribuzione, perSopravvissuta: VoceRef): Esito<Unit> {
        attribuzioni.rimuovi(daRimossa.voceRef)
        attribuzioni.salva(daRimossa.trasferisci(perSopravvissuta))
        val parlante = parlanteDi(daRimossa)
        parlante.trasferisciImpronta(da = daRimossa.voceRef, a = perSopravvissuta)
        return parlanti.salva(parlante)
    }

    private fun parlanteDi(attribuzione: Attribuzione): Parlante =
        checkNotNull(parlanti.trova(attribuzione.parlanteId)) {
            "Attribuzione(${attribuzione.voceRef}) punta al Parlante inesistente ${attribuzione.parlanteId}"
        }
}
