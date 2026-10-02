package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.ConfrontoImpronte
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante

/**
 * Read-model `Proposta` ([INV-20], AC-170..AC-173, AC-308/AC-309, ADR 0017 AC-422/AC-423): the ranked
 * Candidati for a not-yet-attributed `Voce`. The Voce's transient print is extracted from
 * `SorgenteImpronta.di(intervalli)` OUTSIDE any transaction and kept only in memory — never written,
 * never cached to disk (ADR 0009, ADR 0012 Amendment (b) point 2) — and compared only against the
 * `ImprontaVocale`s of the SAME [EstrattoreImpronta.modello] (AC-309): a Parlante whose prints are all
 * of another model has none usable and so is not a Candidato ([INV-20] "≥ 1 ImprontaVocale" read
 * together with AC-309), until `RiallineaImpronte` refreshes them. RC-1: no rule is decided here
 * beyond ranking/shaping the view — [Parlante.attivo] and [ConfrontoImpronte] own the predicates.
 *
 * [INV-20] over the Incontro: the Voce has one transient print per Parte it speaks in, the Galleria holds every
 * print of the Progetto (the other Parti of the same Incontro included), a Candidato's Fascia is the best over the
 * (slice, print) pairs and its extract comes from the Parte of the chosen print ([INV-I17]).
 *
 * The result is cached per `Voce` (AC-422: [EstrattoreImpronta.estrai] is called exactly once per Parte of the
 * Voce per computation, never once per Candidato) until [invalida] is called — by the future Revisione/
 * Attribuzione/`ImpronteRiallineate` subscriber (AC-173: this block only exposes the invalidation, it
 * does not subscribe to `DispatcherEventi` itself, that belongs to `:parlanti:adattatori`). A
 * cancelled computation ([InterruptedException] from [EstrattoreImpronta.estrai], ADR 0017 §1.5)
 * leaves no cache entry — the cache is written only after a computation returns normally (AC-423), so
 * the next request recomputes.
 */
@Suppress("LongParameterList") // one parameter per collaborator: 5 ports + the sibling read-model EstrattoAudio
public class Proposta(
    private val voci: LettoreVoci,
    private val registrazioni: LettoreRegistrazione,
    private val parlanti: ParlanteRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val confronto: ConfrontoImpronte,
    private val estrattoAudio: EstrattoAudio,
) {
    private val cache: MutableMap<VoceRef, PropostaVista> = mutableMapOf()

    /**
     * AC-171/AC-172: read-only, `null` only when [voceRef] has no Trascritto/Registrazione or the
     * Voce is left without any interval (nothing to extract a print from) — an empty gallery still
     * yields a [PropostaVista] with an empty `candidati`.
     */
    public fun perVoce(voceRef: VoceRef): PropostaVista? =
        // AC-423: solo un calcolo riuscito entra in cache.
        cache[voceRef] ?: calcola(voceRef)?.also { cache[voceRef] = it }

    /** AC-173: forgets the cached Proposta of one Voce (its Attribuzione changed). */
    public fun invalida(voceRef: VoceRef) {
        cache.remove(voceRef)
    }

    /**
     * AC-173, ADR 0035 §6: forgets every cached Proposta of the Incontro [incontroId] (a Revisione,
     * `ImpronteRiallineate`, a deleted Parte) — keyed by the event's Incontro, never looked up through a Parte the
     * catalogue may no longer know.
     */
    public fun invalida(incontroId: IncontroId) {
        cache.keys.removeAll { it.incontroId == incontroId }
    }

    private fun calcola(voceRef: VoceRef): PropostaVista? =
        contesto(voceRef)?.let { (progettoId, fette) ->
            // AC-308: bounded by SorgenteImpronta.di, decoded exactly then; AC-422: ONE estrai per Parte, this Voce
            // only — never one per Candidato. [INV-20]: one transient print per Parte the Voce speaks in.
            val impronteVoce = fette.map { (parte, intervalli) ->
                val campioni = decodificatore.campioni(parte, SorgenteImpronta.di(intervalli).intervalli)
                estrattore.estrai(campioni) // puo lanciare InterruptedException (ADR 0017 S1.5): AC-423
            }
            val candidati = parlanti.delProgetto(progettoId)
                .filter { it.attivo }
                .mapNotNull { candidato(it, impronteVoce) }
                .sortedWith(ORDINE_CANDIDATI)
            PropostaVista(voceRef.voceId, candidati)
        }

    /**
     * The Voce's slices — (Parte, intervalli) for every Parte of its Incontro where it has an interval, in the
     * Incontro's order — and the Progetto; `null` when the Voce/Incontro is unknown, has no Trascritto, or no slice.
     */
    private fun contesto(voceRef: VoceRef): Contesto? {
        val perParte = voci.voci(voceRef.incontroId)?.find { it.voceRef == voceRef }?.intervalliPerParte.orEmpty()
        val fette = registrazioni.parti(voceRef.incontroId).orEmpty()
            .map { it.registrazioneId }
            .mapNotNull { parte -> perParte[parte]?.takeIf { it.isNotEmpty() }?.let { parte to it } }
        val progettoId = fette.firstOrNull()?.let { (parte, _) -> registrazioni.registrazione(parte)?.progettoId }
        return progettoId?.let { Contesto(it, fette) }
    }

    /**
     * AC-170/AC-309/[INV-20]: the BEST Fascia over every (Voce slice, print) pair, prints of the current
     * [EstrattoreImpronta.modello] only — from any Parte of any Incontro, the other Parti of this one included.
     * [INV-I17]: the extract comes from the Parte that sourced the chosen print — the first print of that best Fascia
     * whose (Voce, Parte) slice still has an excerpt (a tie falls through to the next equal print, never a worse one).
     * A Parlante whose best-Fascia prints ALL lack an excerpt is not a Candidato (intentional: a worse Fascia is never
     * shown under a better label, and [INV-20] wants an extract for every Candidato; the revisione-policy removes such
     * prints, so this is transient).
     */
    private fun candidato(parlante: Parlante, impronteVoce: List<Impronta>): Candidato? {
        val valutate = parlante.impronte
            .filter { it.modello == estrattore.modello }
            .map { iv -> iv to impronteVoce.minOf { fetta -> confronto.fascia(fetta, listOf(iv.impronta)) } }
        // AC-309: nessuna impronta del modello corrente, non e Candidato
        val migliore = valutate.minOfOrNull { (_, fascia) -> fascia } ?: return null
        // INV-20 "ogni Candidato ha un EstrattoAudio": la (Voce, Parte) sorgente dell'impronta ha ancora intervalli
        // (la revisione-policy rimuove l'impronta della fetta svuotata, ADR 0035 §6).
        return valutate.filter { (_, fascia) -> fascia == migliore }.firstNotNullOfOrNull { (iv, _) ->
            estrattoAudio.estratto(iv.voceRef, iv.parte)?.let { estratto ->
                Candidato(parlante.id, parlante.nome.valore, parlante.tipo.vista(), migliore, estratto)
            }
        }
    }

    private data class Contesto(
        val progettoId: ProgettoId,
        val fette: List<Pair<RegistrazioneId, List<IntervalloMs>>>,
    )

    private companion object {
        val ORDINE_CANDIDATI: Comparator<Candidato> = compareBy(
            { if (it.tipoParlante == TipoParlanteVista.RICORRENTE) 0 else 1 },
            { it.fascia },
            { it.nome },
        )
    }
}

/** `proposta` view_shape: the ranked Candidati of one Voce ([INV-20]). */
public data class PropostaVista(val voceId: VoceId, val candidati: List<Candidato>)

/** One ranked row of [PropostaVista.candidati] — never a numeric score ([INV-20]). */
public data class Candidato(
    val parlanteId: ParlanteId,
    val nome: String,
    val tipoParlante: TipoParlanteVista,
    val fascia: Fascia,
    val estratto: EstrattoRef,
)

private fun TipoParlante.vista(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }
