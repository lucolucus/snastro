package snastro.trascrizione.adattatori.ml

/** One AHC merge: cluster ids [a] and [b] (ids >= n are earlier merges, scipy-style) at distance [distanza]. */
internal data class Unione(val a: Int, val b: Int, val distanza: Double)

/**
 * The symmetric distance matrix of the AHC, condensed (upper triangle, `n(n−1)/2` doubles: about 9 MB for
 * 1 500 pieces, 144 MB at [RaggruppamentoVoci.PEZZI_MASSIMI_AHC]), consumed by [agglomera].
 */
internal class MatriceDistanze private constructor(private val n: Int, private val valori: DoubleArray) {
    private fun indice(i: Int, j: Int): Int {
        val a = minOf(i, j)
        val b = maxOf(i, j)
        return a * n - a * (a + 1) / 2 + (b - a - 1)
    }

    operator fun get(i: Int, j: Int): Double = valori[indice(i, j)]

    private operator fun set(i: Int, j: Int, v: Double) {
        valori[indice(i, j)] = v
    }

    /**
     * `clus.ahc`, merge by merge: take the global minimum (row-major first: lowest i, then lowest j, so
     * i < j), record `(id_i, id_j, d)`, replace row i with `(D[i]·size_i + D[j]·size_j) / (size_i + size_j)`
     * — average linkage weighted by piece COUNT — drop j, and give cluster i the new id `n + t`.
     * Consumes this matrix.
     */
    fun agglomera(): List<Unione> = Agglomerazione().esegui()

    /**
     * Each row caches its minimum (lowest column on ties); only rows whose minimum pointed at i or j are
     * rescanned. Picking the lowest row whose cached minimum is the global minimum is then exactly the
     * full-matrix row-major argmin, including its tie-breaking.
     */
    private inner class Agglomerazione {
        private val vivo = BooleanArray(n) { true }
        private val dimensione = DoubleArray(n) { 1.0 }
        private val id = IntArray(n) { it }
        private val minimo = DoubleArray(n)
        private val colonnaMinimo = IntArray(n)

        fun esegui(): List<Unione> {
            for (r in 0 until n) ricalcolaRiga(r)
            return List(maxOf(n - 1, 0)) { t -> unisci(t) }
        }

        private fun unisci(t: Int): Unione {
            var i = -1
            for (r in 0 until n) if (vivo[r] && (i < 0 || minimo[r] < minimo[i])) i = r
            val j = colonnaMinimo[i]
            val unione = Unione(id[i], id[j], minimo[i])
            val si = dimensione[i]
            val sj = dimensione[j]
            for (c in 0 until n) {
                if (!vivo[c] || c == i || c == j) continue
                valori[indice(i, c)] = (get(i, c) * si + get(j, c) * sj) / (si + sj)
            }
            vivo[j] = false
            dimensione[i] = si + sj
            id[i] = n + t
            aggiornaMinimi(i, j)
            return unione
        }

        private fun aggiornaMinimi(i: Int, j: Int) {
            ricalcolaRiga(i)
            for (r in 0 until n) {
                if (!vivo[r] || r == i) continue
                val puntava = colonnaMinimo[r]
                val d = get(r, i)
                if (puntava == i || puntava == j) {
                    ricalcolaRiga(r)
                } else if (d < minimo[r] || (d == minimo[r] && i < puntava)) {
                    minimo[r] = d
                    colonnaMinimo[r] = i
                }
            }
        }

        private fun ricalcolaRiga(r: Int) {
            var migliore = Double.POSITIVE_INFINITY
            var colonna = -1
            for (c in 0 until n) {
                if (c == r || !vivo[c]) continue
                val d = get(r, c)
                if (d < migliore || colonna < 0) {
                    migliore = d
                    colonna = c
                }
            }
            minimo[r] = migliore
            colonnaMinimo[r] = colonna
        }
    }

    companion object {
        /** `D = 1 − X·Xᵀ` over the normalized embeddings [x]. */
        fun daEmbedding(x: List<DoubleArray>): MatriceDistanze = di(x.size) { i, j ->
            val xi = x[i]
            val xj = x[j]
            var s = 0.0
            for (d in xi.indices) s += xi[d] * xj[d]
            1.0 - s
        }

        /** A matrix from explicit distances (tests: fixtures whose merge order is set by hand). */
        fun di(n: Int, distanza: (Int, Int) -> Double): MatriceDistanze {
            val m = MatriceDistanze(n, DoubleArray(n * (n - 1) / 2))
            for (i in 0 until n) for (j in i + 1 until n) m[i, j] = distanza(i, j)
            return m
        }
    }
}

/**
 * `clus.cut`: the labels of the [n] AHC inputs after the first `n − numeroGruppi` merges, or after the
 * merges with distance ≤ [soglia] (their count); renumbered by first appearance in piece order.
 */
internal fun List<Unione>.taglia(n: Int, numeroGruppi: Int? = null, soglia: Double? = null): IntArray {
    val applicate = if (numeroGruppi != null) n - numeroGruppi else count { it.distanza <= checkNotNull(soglia) }
    val padre = IntArray(2 * n) { it }
    fun radice(x0: Int): Int {
        var x = x0
        while (padre[x] != x) {
            padre[x] = padre[padre[x]]
            x = padre[x]
        }
        return x
    }
    for (t in 0 until applicate) {
        padre[radice(this[t].a)] = n + t
        padre[radice(this[t].b)] = n + t
    }
    val numerazione = HashMap<Int, Int>()
    return IntArray(n) { i -> numerazione.getOrPut(radice(i)) { numerazione.size } }
}
