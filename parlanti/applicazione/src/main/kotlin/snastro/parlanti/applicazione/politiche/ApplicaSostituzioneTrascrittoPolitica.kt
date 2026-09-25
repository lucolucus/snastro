package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Parlante

/**
 * Policy `ApplicaSostituzioneTrascritto` ([INV-15], [INV-25], ADR 0018 §3 + Amendment 2026-09-24 (b)
 * §1): reacts to `TrascrittoSostituito`, run INSIDE the completion transaction that replaced the
 * Trascritto — and (ADR 0020 §2 step 4, [INV-28]) to `RegistrazioneEliminata`, run INSIDE the deleting
 * transaction of `EliminaRegistrazione`, reused unchanged. It never imports either published event
 * (`:parlanti:applicazione` may not depend on `:trascrizione:applicazione` nor `:progetto:applicazione`'s
 * events, `architecture.md` edges table): `abbonato-revisione-parlanti` (`:parlanti:adattatori`)
 * subscribes to both and translates each into the call below.
 *
 * STRUCTURAL part only (ADR 0012 Amendment (b) points 3-4): it never decodes nor extracts, and
 * attempts NO old→new Voce mapping (ADR 0018 §3 — a heuristic carry-over would pin a biometric print
 * to the wrong person). Every `Attribuzione` and every print row keyed by a `VoceRef` of
 * [applica]'s `registrazioneId` is purged, for every [Parlante] — `attivo` and `eliminato` alike —
 * including, defensively, a print row with no `Attribuzione` ([INV-15] says such a row cannot exist;
 * this purges it anyway so no row keyed by an old `VoceRef` survives into the new generation). Then
 * [INV-25] applies: an `attivo occasionale` left without any `Attribuzione` ceases to exist
 * (`ParlanteRepository.rimuovi`); a `ricorrente` is always kept, even at zero `Attribuzione`s and
 * zero prints; an `eliminato` tombstone is kept, only its `Attribuzione` in this Registrazione goes.
 * Every rule goes through [Parlante] / [Attribuzione] (RC-1); this class only orchestrates lookups.
 */
public class ApplicaSostituzioneTrascrittoPolitica(
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
) {
    public fun applica(registrazioneId: RegistrazioneId): Esito<Unit> {
        val attribuzioniDiR = attribuzioni.diRegistrazione(registrazioneId)
        val improntePurgare = parlanti.impronteDiRegistrazione(registrazioneId)
        val vociPerParlante = vociPerParlante(attribuzioniDiR, improntePurgare)
        attribuzioniDiR.forEach { attribuzioni.rimuovi(it.voceRef) }
        return purgaOgniParlante(vociPerParlante)
    }

    /** Every `VoceRef` of the Registrazione to purge, grouped by the [Parlante] that holds it. */
    private fun vociPerParlante(
        attribuzioniDaPurgare: List<Attribuzione>,
        improntePurgare: List<RigaImpronta>,
    ): Map<ParlanteId, Set<VoceRef>> {
        val risultato = mutableMapOf<ParlanteId, MutableSet<VoceRef>>()
        attribuzioniDaPurgare.forEach { risultato.getOrPut(it.parlanteId) { mutableSetOf() }.add(it.voceRef) }
        improntePurgare.forEach { risultato.getOrPut(it.parlanteId) { mutableSetOf() }.add(it.voceRef) }
        return risultato
    }

    /**
     * Purges each [Parlante]'s share of `vociPerParlante`. `fold` + [poi]: the first
     * [Esito.Errore] from [ParlanteRepository.salva] short-circuits the rest and is returned
     * unchanged (AC-431) — nothing here swallows it, so the caller's transaction rolls back.
     */
    private fun purgaOgniParlante(vociPerParlante: Map<ParlanteId, Set<VoceRef>>): Esito<Unit> {
        val ok: Esito<Unit> = Esito.Ok(Unit)
        return vociPerParlante.entries.fold(ok) { esito, (parlanteId, voceRefs) ->
            esito.poi { purgaParlante(parlanteId, voceRefs) }
        }
    }

    /** [INV-15] removes every [voceRefs] print of this [Parlante], then [INV-25]'s cessation check. */
    private fun purgaParlante(parlanteId: ParlanteId, voceRefs: Set<VoceRef>): Esito<Unit> {
        val parlante = parlanteDi(parlanteId)
        voceRefs.forEach(parlante::rimuoviImpronta)
        return parlanti.salva(parlante).mappa {
            if (parlante.attivo && parlante.occasionale && attribuzioni.diParlante(parlante.id).isEmpty()) {
                parlanti.rimuovi(parlante.id)
            }
        }
    }

    private fun parlanteDi(id: ParlanteId): Parlante =
        checkNotNull(parlanti.trova(id)) {
            "Un'Attribuzione o un'impronta della Registrazione punta al Parlante inesistente $id"
        }
}
