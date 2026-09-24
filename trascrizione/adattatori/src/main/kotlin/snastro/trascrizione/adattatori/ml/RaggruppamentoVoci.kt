package snastro.trascrizione.adattatori.ml

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One piece of a step-1 segment, in seconds — the experiment's own unit (ADR 0019 §1.9). */
internal data class Pezzo(val inizioS: Double, val fineS: Double) {
    val durataS: Double get() = fineS - inizioS
}

/**
 * Steps 2, 4 and 5 of ADR 0019 §1.2 — pure Kotlin, no native, no Mutex: pieces, average-linkage cosine
 * AHC, the k-cut / threshold cut with the qualifying minimum, duration-weighted centroids and
 * nearest-centroid assignment. It reproduces the experiment's `clus.py` exactly (ADR 0019 §1.9), plus
 * the three §1.2 additions: the qualifying minimum `min(60 s, 10 % of speech)`, the "< k voices" and
 * "nothing qualifies" rules, and the [PEZZI_MASSIMI_AHC] subsample. Every tie breaks on the lowest index.
 */
internal object RaggruppamentoVoci {
    const val PEZZO_MS: Int = 3_000
    const val DURATA_MINIMA_PEZZO_AHC_MS: Int = 1_500
    const val DURATA_MINIMA_CLUSTER_MS: Int = 60_000
    const val QUOTA_MINIMA_CLUSTER: Double = 0.10
    const val PEZZI_MASSIMI_AHC: Int = 6_000
    const val SOGLIA_AHC_AUTO: Double = 0.5

    private const val MS_PER_S = 1_000.0
    private const val EPSILON_NORMA = 1e-9

    /**
     * Step 2 (`clus.pieces`): each segment `[s, e)` in seconds becomes `max(1, ceil((e − s) / 3 s))` equal,
     * non-overlapping pieces; never across a segment boundary; overlapping segments all keep their pieces.
     */
    fun pezzi(segmenti: List<Pair<Double, Double>>): List<Pezzo> = segmenti.flatMap { (s, e) ->
        val n = max(1, ceil((e - s) / (PEZZO_MS / MS_PER_S)).toInt())
        List(n) { j -> Pezzo(s + (e - s) * j / n, s + (e - s) * (j + 1) / n) }
    }

    /** `v / (‖v‖₂ + 1e-9)` (`common.embed`). */
    fun normalizza(v: FloatArray): DoubleArray {
        var somma = 0.0
        for (x in v) somma += x.toDouble() * x
        val norma = sqrt(somma) + EPSILON_NORMA
        return DoubleArray(v.size) { v[it] / norma }
    }

    /** The qualifying speech of one cluster: `min(60 s, 10 % × total speech)`, in seconds. */
    fun durataMinimaGruppoS(parlatoTotaleS: Double): Double =
        min(DURATA_MINIMA_CLUSTER_MS / MS_PER_S, QUOTA_MINIMA_CLUSTER * parlatoTotaleS)

    /**
     * Steps 4 and 5: the voice index of every piece (same order as [pezzi]); [embedding] are normalized.
     * [numeroPersone] = k → the k-cut (at most k voices); `null` → the [SOGLIA_AHC_AUTO] cut.
     */
    fun voci(pezzi: List<Pezzo>, embedding: List<DoubleArray>, numeroPersone: Int?): IntArray {
        require(pezzi.size == embedding.size) { "one embedding per piece" }
        if (pezzi.isEmpty()) return IntArray(0)
        val adatti = pezzi.indices.filter { pezzi[it].durataS >= DURATA_MINIMA_PEZZO_AHC_MS / MS_PER_S }
        val passo = if (adatti.size > PEZZI_MASSIMI_AHC) ceil(adatti.size.toDouble() / PEZZI_MASSIMI_AHC).toInt() else 1
        val campione = campioneAhc(adatti, passo)
        val x = campione.map { embedding[it] }
        // A subsampled piece stands for `passo` pieces: its weight in the qualifying rule scales with it.
        val durate = DoubleArray(campione.size) { pezzi[campione[it]].durataS * passo }
        val minimoS = durataMinimaGruppoS(pezzi.sumOf { it.durataS })
        val scelta = if (campione.isEmpty()) null else scegli(x, durate, minimoS, numeroPersone)
        // No piece fit for the AHC, or nothing qualifies with k given: one voice with all the speech.
        val centroidi = scelta?.let { centroidi(x, durate, it.etichette, it.gruppi) }
        return IntArray(pezzi.size) { if (centroidi == null) 0 else piuVicino(embedding[it], centroidi) }
    }

    /** Every [passo]-th fit piece (deterministic, ADR 0019 "Large inputs"). */
    fun campioneAhc(adatti: List<Int>, passo: Int): List<Int> = adatti.filterIndexed { i, _ -> i % passo == 0 }

    private fun scegli(x: List<DoubleArray>, durate: DoubleArray, minimoS: Double, numeroPersone: Int?): Scelta? {
        val unioni = MatriceDistanze.daEmbedding(x).agglomera()
        return if (numeroPersone == null) {
            sceltaAutomatica(unioni.taglia(x.size, soglia = SOGLIA_AHC_AUTO), durate, minimoS)
        } else {
            sceltaConNumero(unioni, x.size, durate, minimoS, numeroPersone)
        }
    }

    /** The kept clusters (centroid order) over the AHC input's [etichette]. */
    class Scelta(val etichette: IntArray, val gruppi: List<Int>)

    private fun parlatoPerGruppo(etichette: IntArray, durate: DoubleArray): DoubleArray {
        val du = DoubleArray((etichette.maxOrNull() ?: -1) + 1)
        for (i in etichette.indices) du[etichette[i]] += durate[i]
        return du
    }

    /** Auto: the qualifying clusters in label order, or the one with the most speech if none qualifies. */
    private fun sceltaAutomatica(etichette: IntArray, durate: DoubleArray, minimoS: Double): Scelta {
        val du = parlatoPerGruppo(etichette, durate)
        val qualificati = du.indices.filter { du[it] >= minimoS }
        return Scelta(etichette, qualificati.ifEmpty { listOf(du.indices.maxBy { du[it] }) })
    }

    /**
     * k given: cut at m = k, k+1, … until ≥ k clusters qualify, then keep the k with the most speech. If no
     * m does, the qualifying clusters of the first m where they are most (< k voices); `null` if never any.
     */
    private fun sceltaConNumero(
        unioni: List<Unione>,
        n: Int,
        durate: DoubleArray,
        minimoS: Double,
        k: Int,
    ): Scelta? {
        var migliore: Scelta? = null
        for (m in min(k, n)..n) {
            val etichette = unioni.taglia(n, numeroGruppi = m)
            val du = parlatoPerGruppo(etichette, durate)
            val qualificati = du.indices.filter { du[it] >= minimoS }.sortedByDescending { du[it] }
            if (qualificati.size >= k) return Scelta(etichette, qualificati.take(k))
            if (qualificati.size > (migliore?.gruppi?.size ?: 0)) migliore = Scelta(etichette, qualificati)
        }
        return migliore
    }
}

/** `clus.centroids`: per kept cluster, Σ durata·x over its members, then L2-normalized (no epsilon). */
internal fun centroidi(
    x: List<DoubleArray>,
    durate: DoubleArray,
    etichette: IntArray,
    gruppi: List<Int>,
): List<DoubleArray> {
    val dimensione = x.first().size
    return gruppi.map { g ->
        val c = DoubleArray(dimensione)
        for (i in x.indices) {
            if (etichette[i] == g) for (d in 0 until dimensione) c[d] += x[i][d] * durate[i]
        }
        val norma = sqrt(c.sumOf { it * it })
        if (norma > 0.0) for (d in c.indices) c[d] /= norma
        c
    }
}

/** `argmax(e · Cᵀ)`: the lowest index wins a tie. */
internal fun piuVicino(e: DoubleArray, centroidi: List<DoubleArray>): Int {
    var migliore = 0
    var massimo = Double.NEGATIVE_INFINITY
    centroidi.forEachIndexed { indice, c ->
        var s = 0.0
        for (d in e.indices) s += e[d] * c[d]
        if (s > massimo) {
            massimo = s
            migliore = indice
        }
    }
    return migliore
}
