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

/**
 * Read-model `PropostaTraParti` ([INV-I18], ADR 0036, AC-I48/AC-I49): per Incontro, the pairs of not-yet-attributed
 * Voci of DIFFERENT Parti that are each other's only FORTE. Never automatic, never written: the transient prints
 * live in memory only (ADR 0009), extracted outside any transaction, ONE `estrai` per eligible (Voce, Parte) slice
 * per computation (ADR 0017). The similarity of two Voci is the best [Fascia] over their slice pairs, judged by
 * [ConfrontoImpronte] — the same `SoglieFascia` as [Proposta]. No rule beyond shaping the view lives here.
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
    private val cache: MutableMap<IncontroId, List<CoppiaTraParti>> = mutableMapOf()

    /** [INV-I18]: the pairs of [incontroId], `voceA`'s first Parte before `voceB`'s; empty when none or unknown. */
    public fun perIncontro(incontroId: IncontroId): List<CoppiaTraParti> =
        cache[incontroId] ?: calcola(incontroId).also { cache[incontroId] = it } // AC-I49: only a finished computation

    /** AC-I49: forgets the cached proposal of one Incontro. */
    public fun invalida(incontroId: IncontroId) {
        cache.remove(incontroId)
    }

    private fun calcola(incontroId: IncontroId): List<CoppiaTraParti> {
        val numeri = registrazioni.parti(incontroId).orEmpty().associate { it.registrazioneId to it.numero }
        val idonee = voci.voci(incontroId).orEmpty()
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
        val forti = idonee.associate { (a, fetteA) ->
            a to idonee.map { it.first }.filter { b ->
                val fetteB = impronte.getValue(b)
                b != a && fetteA.keys.none { it in fetteB } && forte(impronte.getValue(a), fetteB)
            }
        }
        return forti.mapNotNull { (a, candidati) ->
            val b = candidati.singleOrNull()
            b?.takeIf { forti.getValue(it) == listOf(a) }?.let { coppia(a, it, numeri, impronte) }
        }.sortedBy { it.voceA.numero }
    }

    /** Best Fascia over the slice pairs is FORTE. */
    private fun forte(a: Map<RegistrazioneId, Impronta>, b: Map<RegistrazioneId, Impronta>): Boolean =
        a.values.any { fa -> confronto.fascia(fa, b.values.toList()) == Fascia.FORTE }

    /** Keeps each pair once, with the Voce whose first Parte is earlier as A; `null` when an excerpt is gone. */
    private fun coppia(
        a: VoceRef,
        b: VoceRef,
        numeri: Map<RegistrazioneId, Int>,
        impronte: Map<VoceRef, Map<RegistrazioneId, Impronta>>,
    ): CoppiaTraParti? {
        fun prima(v: VoceRef) = impronte.getValue(v).keys.minOf { numeri.getValue(it) }
        val (x, y) = if (prima(a) < prima(b)) a to b else b to a
        return if (x != a) {
            null // the pair is built from A's side only
        } else {
            val estrattoA = estrattoAudio.estratto(x)
            val estrattoB = estrattoAudio.estratto(y)
            if (estrattoA == null || estrattoB == null) {
                null
            } else {
                CoppiaTraParti(x.voceId, prima(x), estrattoA, y.voceId, prima(y), estrattoB)
            }
        }
    }
}

/** One pair of [PropostaTraParti.perIncontro]: [parteA] < [parteB], the Voci's first Parti ([INV-I2]); A survives. */
public data class CoppiaTraParti(
    val voceA: VoceId,
    val parteA: Int,
    val estrattoA: EstrattoRef,
    val voceB: VoceId,
    val parteB: Int,
    val estrattoB: EstrattoRef,
)
