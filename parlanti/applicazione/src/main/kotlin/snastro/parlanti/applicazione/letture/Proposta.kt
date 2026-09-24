package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
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
 * The result is cached per `Voce` (AC-422: [EstrattoreImpronta.estrai] is called exactly once per
 * computation, never once per Candidato) until [invalida] is called — by the future Revisione/
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

    /** AC-173: forgets every cached Proposta of a Registrazione (a Revisione, `ImpronteRiallineate`). */
    public fun invalida(registrazioneId: RegistrazioneId) {
        cache.keys.removeAll { it.registrazioneId == registrazioneId }
    }

    private fun calcola(voceRef: VoceRef): PropostaVista? =
        contesto(voceRef)?.let { (progettoId, intervalli) ->
            // AC-308: bounded by SorgenteImpronta.di, decoded exactly then; AC-422: ONE estrai, this Voce only.
            val sorgente = SorgenteImpronta.di(intervalli)
            val campioni = decodificatore.campioni(voceRef.registrazioneId, sorgente.intervalli)
            val impronta = estrattore.estrai(campioni) // puo lanciare InterruptedException (ADR 0017 S1.5): AC-423
            val candidati = parlanti.delProgetto(progettoId)
                .filter { it.attivo }
                .mapNotNull { candidato(it, impronta) }
                .sortedWith(ORDINE_CANDIDATI)
            PropostaVista(voceRef.voceId, candidati)
        }

    /** `null` when [voceRef] has no Trascritto/Registrazione, or its Voce has no interval left. */
    private fun contesto(voceRef: VoceRef): Contesto? =
        registrazioni.registrazione(voceRef.registrazioneId)?.progettoId?.let { progettoId ->
            voci.voci(voceRef.registrazioneId)
                ?.find { it.voceRef == voceRef }
                ?.intervalli
                ?.takeIf { it.isNotEmpty() }
                ?.let { intervalli -> Contesto(progettoId, intervalli) }
        }

    /** AC-170/AC-309: the BEST Fascia among the impronte of the current [EstrattoreImpronta.modello] only. */
    private fun candidato(parlante: Parlante, improntaVoce: Impronta): Candidato? =
        parlante.impronte
            .filter { it.modello == estrattore.modello }
            .map { it to confronto.fascia(improntaVoce, listOf(it.impronta)) }
            .minByOrNull { (_, fascia) -> fascia } // AC-309: nessuna impronta del modello corrente, non e Candidato
            ?.let { (impronta, fascia) ->
                // INV-20 "ogni Candidato ha un EstrattoAudio": per costruzione la Voce sorgente dell'impronta
                // esiste ancora (la revisione-policy rimuove impronta e Attribuzione insieme, ADR 0012 (b) S3).
                estrattoAudio.estratto(impronta.voceRef)?.let { estratto ->
                    Candidato(parlante.id, parlante.nome.valore, parlante.tipo.vista(), fascia, estratto)
                }
            }

    private data class Contesto(val progettoId: ProgettoId, val intervalli: List<IntervalloMs>)

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
