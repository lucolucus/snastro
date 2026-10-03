package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ConfrontoImpronte
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.SorgenteImpronta
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-model `PropostaTraParti` ([INV-I18], ADR 0036, AC-I48/AC-I49): per Incontro, the pairs of not-yet-attributed
 * Voci sharing no Parte that are each other's only FORTE among ALL the eligible Voci (D-0057). Never automatic, never
 * written: the transient prints live in memory only (ADR 0009), extracted outside any transaction, ONE `estrai` per
 * eligible (Voce, Parte) slice per computation (ADR 0017). The similarity of two Voci is the best [Fascia] over their
 * slice pairs, judged by [ConfrontoImpronte] — the same `SoglieFascia` as [Proposta]. No rule beyond shaping the
 * view lives here.
 *
 * The result is cached per Incontro until [invalida] — called by the subscriber of `VociUnite`, `VoceDivisa`,
 * `SegmentoRiassegnato`, `ElaborazioneCompletata`, `TrascrittoSostituito`, `TrascrittoEliminato`,
 * `AttribuzioneConfermata`, `ImpronteRiallineate` (it belongs to `:parlanti:adattatori`, this block only exposes the
 * invalidation). A cancelled computation ([InterruptedException] from [EstrattoreImpronta.estrai]) leaves no entry.
 */
@Suppress("LongParameterList") // one parameter per collaborator
public class PropostaTraParti(
    private val voci: LettoreVoci,
    private val registrazioni: LettoreRegistrazione,
    private val attribuzioni: AttribuzioneRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val confronto: ConfrontoImpronte,
    private val estrattoAudio: EstrattoAudio,
) {
    /** Thread-safe: `perIncontro` runs on the multi-threaded io dispatcher, `invalida` on the committing thread. */
    private val cache = ConcurrentHashMap<IncontroId, List<CoppiaTraParti>>()

    /** Per-Incontro generation: bumped by [invalida]; a result computed under an older one is never stored. */
    private val generazioni = ConcurrentHashMap<IncontroId, Long>()

    /**
     * [INV-I18]: the pairs of [incontroId] ([CoppiaTraParti]), by `voceA`; empty when none or unknown. A failure of
     * the extractor or the decoder (and an [InterruptedException] on cancellation) propagates to the caller and caches
     * nothing: the consumer guards it, as for any port call.
     */
    public fun perIncontro(incontroId: IncontroId): List<CoppiaTraParti> {
        cache[incontroId]?.let { return it }
        val generazione = generazioni[incontroId] ?: 0L
        val calcolato = calcola(incontroId) // AC-I49: only a finished computation is ever stored
        generazioni.compute(incontroId) { _, attuale ->
            // Atomic with invalida's bump: stored only if no invalida ran since the computation began.
            if ((attuale ?: 0L) == generazione) cache[incontroId] = calcolato
            attuale
        }
        return calcolato
    }

    /** AC-I49: forgets the cached proposal of one Incontro, and any computation still in flight for it. */
    public fun invalida(incontroId: IncontroId) {
        generazioni.compute(incontroId) { _, attuale ->
            cache.remove(incontroId)
            (attuale ?: 0L) + 1
        }
    }

    private fun calcola(incontroId: IncontroId): List<CoppiaTraParti> {
        val numeri = registrazioni.parti(incontroId).orEmpty().associate { it.registrazioneId to it.numero }
        // A 1-Parte Incontro never has a pair: no read, no extraction under the shared ML lock.
        val idonee = (if (numeri.size < 2) null else voci.voci(incontroId)).orEmpty()
            .filter { attribuzioni.trova(it.voceRef) == null }
            .map { v -> v.voceRef to v.intervalliPerParte.filterKeys { it in numeri }.filterValues { it.isNotEmpty() } }
            .filter { (_, fette) -> fette.isNotEmpty() }
        if (idonee.size < 2) return emptyList()

        // AC-I48: ONE extraction per eligible slice (ADR 0017), in memory only.
        val impronte: Map<VoceRef, Map<RegistrazioneId, Impronta>> = idonee.associate { (ref, fette) ->
            ref to fette.mapValues { (parte, intervalli) ->
                estrattore.estrai(decodificatore.campioni(parte, SorgenteImpronta.di(intervalli).intervalli))
            }
        }
        // [INV-I18] strict reading (D-0057): EVERY eligible FORTE counts as a rival, a Voce sharing a Parte included;
        // only a mutual, unique FORTE sharing no Parte with A is paired.
        val forti = idonee.associate { (a, _) ->
            a to idonee.map { it.first }.filter { b -> b != a && forte(impronte.getValue(a), impronte.getValue(b)) }
        }
        fun prima(v: VoceRef) = impronte.getValue(v).keys.minOf { numeri.getValue(it) }
        fun disgiunte(a: VoceRef, b: VoceRef) = impronte.getValue(a).keys.none { it in impronte.getValue(b) }
        fun reciproca(a: VoceRef, b: VoceRef) = forti.getValue(b) == listOf(a) && disgiunte(a, b)
        return forti.mapNotNull { (a, candidati) ->
            candidati.singleOrNull()?.takeIf { reciproca(a, it) }?.let { setOf(a, it) }
        }
            .distinct() // each mutual pair is found from both of its Voci: kept once
            .map { it.sortedBy(::prima) } // [INV-I2] the Voce whose first Parte is earlier is A, the survivor
            .mapNotNull { (a, b) -> coppia(a, b, numeri) }
            .sortedBy { it.voceA.numero }
    }

    /** Best Fascia over the slice pairs is FORTE. */
    private fun forte(a: Map<RegistrazioneId, Impronta>, b: Map<RegistrazioneId, Impronta>): Boolean =
        a.values.any { fa -> confronto.fascia(fa, b.values.toList()) == Fascia.FORTE }

    /** The view of the pair (A survives), each Parte the one its excerpt plays from (D-0057); `null` if one is gone. */
    private fun coppia(a: VoceRef, b: VoceRef, numeri: Map<RegistrazioneId, Int>): CoppiaTraParti? {
        // a Parte added since `numeri` was read has no number: no pair now, the next computation has it
        fun lato(v: VoceRef) = estrattoAudio.estratto(v)?.let { e -> numeri[e.registrazioneId]?.let { it to e } }
        val la = lato(a)
        val lb = lato(b)
        return if (la == null || lb == null) {
            null
        } else {
            CoppiaTraParti(a.voceId, la.first, la.second, b.voceId, lb.first, lb.second)
        }
    }
}

/**
 * One pair of [PropostaTraParti.perIncontro]. [voceA] is the Voce whose first Parte is earlier ([INV-I2]): it survives
 * the join. [parteA] / [parteB] are the numbers (1..N in the Incontro's order) of the Parte [estrattoA] / [estrattoB]
 * plays from — the Parte where that Voce speaks most ([INV-I17], D-0057) — so the banner names the Parte the user
 * hears; they always differ, but [parteA] may be greater than [parteB].
 */
public data class CoppiaTraParti(
    val voceA: VoceId,
    val parteA: Int,
    val estrattoA: EstrattoRef,
    val voceB: VoceId,
    val parteB: Int,
    val estrattoB: EstrattoRef,
)
