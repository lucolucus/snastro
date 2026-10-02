package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Parlante

/**
 * Policy `ApplicaSostituzioneTrascritto` ([INV-I8b], [INV-25], ADR 0035 §6, ADR 0038 §2): reacts, INSIDE the
 * publishing transaction, to `TrascrittoSostituito` (completion unit of a re-transcribed Parte) and to
 * `TrascrittoEliminato` (deleting unit of a Parte). It never imports those published events
 * (`:parlanti:applicazione` may not depend on `:trascrizione:applicazione`, `architecture.md` edges table):
 * `abbonato-revisione-parlanti` (`:parlanti:adattatori`) subscribes to both and translates each into [applica].
 *
 * STRUCTURAL part only (ADR 0012 Amendment (b) points 3-4): it never decodes nor extracts, and attempts NO old→new
 * Voce mapping (ADR 0018 §3). PER PARTE: it removes EVERY print sourced from the Parte `registrazioneId` (any Voce,
 * any [Parlante], `attivo` and `eliminato` alike, defensively also a print with no `Attribuzione`) and the
 * `Attribuzione` of each Voce in `vociRimosse` (with any print left for those Voci, which have no source anywhere).
 * A surviving Voce keeps its `Attribuzione` and its prints of the other Parti. Then [INV-25] applies: an
 * `attivo occasionale` left without any `Attribuzione` ceases to exist (`ParlanteRepository.rimuovi`); a `ricorrente`
 * is always kept; an `eliminato` tombstone is kept. Every removal is persisted through `ParlanteRepository.salva`, so
 * the adapter's after-commit WAL checkpoint (ADR 0009) fires. Every rule goes through [Parlante] / [Attribuzione]
 * (RC-1); this class only orchestrates lookups.
 */
public class ApplicaSostituzioneTrascrittoPolitica(
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
) {
    /** Purges the Parte [registrazioneId] of [incontroId]; [vociRimosse] are the Voci that ceased with it. */
    public fun applica(
        registrazioneId: RegistrazioneId,
        incontroId: IncontroId,
        vociRimosse: Set<VoceId>,
    ): Esito<Unit> {
        val voceRefRimosse = vociRimosse.mapTo(HashSet()) { VoceRef(incontroId, it) }
        val attribuzioniRimosse = voceRefRimosse.mapNotNull(attribuzioni::trova)
        val improntePurgare = parlanti.impronteDiRegistrazione(registrazioneId)
        val parlantiColpiti = attribuzioniRimosse.map { it.parlanteId }.toSet() + improntePurgare.map { it.parlanteId }
        attribuzioniRimosse.forEach { attribuzioni.rimuovi(it.voceRef) }
        return parlantiColpiti.fold<ParlanteId, Esito<Unit>>(Esito.Ok(Unit)) { esito, id ->
            esito.poi { purgaParlante(id, registrazioneId, voceRefRimosse) }
        }
    }

    /**
     * Transitional (until `eliminazione-parte-trascrizione` publishes `TrascrittoEliminato` from the deleting unit,
     * ADR 0038 §2): `RegistrazioneEliminata` of the LAST Parte ([incontroCessato]) ends every Voce of the Incontro; a
     * non-last Parte purges its prints only. The Voci a non-last Parte's removal ends are unknown here (Parlanti never
     * reads Trascrizione's mind), so multi-Parte elimination stays behind the capability flag until I2.
     */
    public fun applicaEliminazioneRegistrazione(
        registrazioneId: RegistrazioneId,
        incontroId: IncontroId,
        incontroCessato: Boolean,
    ): Esito<Unit> {
        val voci = if (incontroCessato) {
            attribuzioni.diIncontro(incontroId).mapTo(HashSet()) { it.voceRef.voceId }
        } else {
            emptySet()
        }
        return applica(registrazioneId, incontroId, voci)
    }

    /** [INV-I8b] removes the prints of [parte] and of the ceased [voceRimosse] of this [Parlante], then [INV-25]. */
    private fun purgaParlante(parlanteId: ParlanteId, parte: RegistrazioneId, voceRimosse: Set<VoceRef>): Esito<Unit> {
        val parlante = parlanteDi(parlanteId)
        parlante.rimuoviImpronteDellaParte(parte)
        voceRimosse.forEach(parlante::rimuoviImpronta) // a ceased Voce has no source in any Parte
        return parlanti.salva(parlante).mappa {
            if (parlante.attivo && parlante.occasionale && attribuzioni.diParlante(parlante.id).isEmpty()) {
                parlanti.rimuovi(parlante.id)
            }
        }
    }

    private fun parlanteDi(id: ParlanteId): Parlante =
        checkNotNull(parlanti.trova(id)) {
            "Un'Attribuzione o un'impronta della Parte punta al Parlante inesistente $id"
        }
}
